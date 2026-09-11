package io.github.lvdaxianer.dinghai.sharedstate;

import org.springframework.data.redis.core.script.RedisScript;

/**
 * 共享状态 Redis 原子脚本集合。
 *
 * <p>比较更新、读取并删除、计数器递增都需要"读-判-写"原子完成，Lua 脚本在 Redis
 * 服务端单线程执行，可避免跨节点并发下的竞态；计数器脚本额外用十进制字符串做高精度
 * 运算，规避 Lua 双精度浮点在 Long 边界附近的精度丢失。</p>
 *
 * @author lvdaxianer@yeah.net
 * @date 2026-07-17
 */
final class RedisLuaScripts {

    /**
     * 比较并更新脚本。
     *
     * <p>仅当当前信封文本与期望值逐字节一致时才覆盖写入，返回 1 表示成功、0 表示失败。</p>
     */
    static final RedisScript<Long> COMPARE_AND_SET = RedisScript.of("""
            -- 比较并更新（CAS）脚本。
            -- 意图：比较与写入必须在服务端原子完成，避免并发下"读后写"竞态。
            -- 读取当前信封文本，准备与期望值比较。
            local current = redis.call('GET', KEYS[1])
            -- 只有当前值与期望值完全一致时才执行覆盖写，保证比较更新的原子性。
            if current == ARGV[1] then
              redis.call('SET', KEYS[1], ARGV[2])
              -- ARGV[3] 为 '0' 表示永久有效，非 0 时按毫秒设置过期时间。
              if ARGV[3] ~= '0' then redis.call('PEXPIRE', KEYS[1], ARGV[3]) end
              -- 返回 1 表示更新成功。
              return 1
            end
            -- 当前值不匹配或键不存在时返回 0，由调用方按更新失败处理。
            return 0
            """, Long.class);

    /** 原子条件删除脚本。 */
    static final RedisScript<Long> COMPARE_AND_DELETE = RedisScript.of("""
            local current = redis.call('GET', KEYS[1])
            if current == ARGV[1] then
              redis.call('DEL', KEYS[1])
              return 1
            end
            return 0
            """, Long.class);

    /** 原子读取并替换脚本。 */
    static final RedisScript<String> GET_AND_SET = RedisScript.of("""
            local previous = redis.call('GET', KEYS[1])
            redis.call('SET', KEYS[1], ARGV[1])
            if ARGV[2] ~= '0' then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            return previous
            """, String.class);

    /**
     * 原子读取并滑动续期脚本。
     *
     * <p>不依赖 Redis 6.2 的 GETEX：读取与 TTL 重置在脚本内原子完成，
     * 键不存在时不创建键。</p>
     */
    static final RedisScript<String> GET_AND_EXPIRE = RedisScript.of("""
            -- 读取并滑动续期脚本。
            -- 意图：读取与 TTL 重置必须在服务端原子完成，避免续期窗口内键过期丢失。
            local value = redis.call('GET', KEYS[1])
            -- 键不存在时直接返回 nil，不创建键，调用方将其映射为空结果。
            if not value then return value end
            -- ARGV[1] 为 '0' 表示续期为永久（PERSIST），非 0 时按毫秒重置过期时间。
            if ARGV[1] == '0' then
              redis.call('PERSIST', KEYS[1])
            else
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return value
            """, String.class);

    /**
     * 兼容 Redis 3.2 的原子读取并删除脚本。
     *
     * <p>旧版 Redis 没有 GETDEL 命令，用脚本模拟同样的原子"消费"语义。</p>
     */
    static final RedisScript<String> GET_AND_DELETE = RedisScript.of("""
            -- 读取并删除脚本。
            -- 意图：一次性消费场景要求"读"与"删"之间不允许插入其他命令。
            -- 先读出当前值再删键，模拟 Redis 6.2 之前缺失的 GETDEL 语义。
            local value = redis.call('GET', KEYS[1])
            -- 键不存在时跳过删除并返回 nil，调用方将其映射为空结果。
            if value then redis.call('DEL', KEYS[1]) end
            return value
            """, String.class);

    /**
     * 精确执行有符号十进制加法，避免 Lua 浮点数丢失 Long 精度。
     *
     * <p>Lua 数字是双精度浮点，整数超过 2^53 后无法精确表示，而共享状态计数器
     * 需要精确到达 Long 边界（±2^63），因此脚本把整数当作十进制字符串逐位运算。</p>
     */
    private static final String INCREMENT_LUA = """
            -- 计数器原子递增脚本（高精度实现）。
            -- 参数：KEYS[1] 计数器键；ARGV[1] 增量十进制文本；
            --       ARGV[2] 键不存在时的初始信封模板；ARGV[3] TTL 毫秒（'0' 表示永久）。
            -- 返回：累加后的十进制文本；信封非法或数值越界时通过 error_reply 显式失败。

            -- 去除十进制数字串的前导零（如 '007' 归一为 '7'）。
            -- 边界：输入全为零时 gsub 得到空串，必须显式归一为 '0'，
            -- 否则后续按长度比较大小会出错。
            local function strip(value)
              local result = string.gsub(value, '^0+', '')
              if result == '' then return '0' end
              return result
            end

            -- 拆分有符号数字串为符号（-1/1）与无符号数值部分。
            -- 边界：'-0' 会归一为符号 -1、值 '0'，不影响后续加减结果。
            local function split(value)
              if string.sub(value, 1, 1) == '-' then
                return -1, strip(string.sub(value, 2))
              end
              return 1, strip(value)
            end

            -- 比较两个无符号十进制数字串的大小，返回 1 / 0 / -1。
            -- 前置条件：输入均已去除前导零，因此先比长度、再等长比字典序即可。
            local function compare_positive(left, right)
              -- 长度不同则位数多者更大。
              if string.len(left) ~= string.len(right) then
                return string.len(left) > string.len(right) and 1 or -1
              end
              if left == right then return 0 end
              -- 等长数字串的字典序与数值序一致。
              return left > right and 1 or -1
            end

            -- 取数字串第 index 位（从右往左的位置索引）的数字值。
            -- 边界：index <= 0 表示该数位不存在，按 0 处理，用于长短数对齐。
            local function digit(value, index)
              if index <= 0 then return 0 end
              return tonumber(string.sub(value, index, index))
            end

            -- 反转字符表：逐位运算的结果是低位在前，输出前需反转为高位在前。
            -- 说明：add_positive 与 subtract_positive 都按低位优先写入结果表，
            -- 因此统一在这里做一次反转，避免各算法段重复处理顺序问题。
            local function reverse(characters)
              local output = {}
              for index = #characters, 1, -1 do
                output[#output + 1] = characters[index]
              end
              return table.concat(output)
            end

            -- 删除低位在前表示中的尾部零（即数值的前导零）。
            -- 边界：至少保留一位，结果为全零时保留单个 '0'，防止删成空串。
            local function trim_zeroes(characters)
              while #characters > 1 and characters[#characters] == '0' do
                table.remove(characters)
              end
              return characters
            end

            -- 无符号十进制加法（高精度核心段）。
            -- 意图：从最低位开始逐位相加并向上进位，全程只处理单个数字字符，
            -- 因此不受 Lua 浮点精度限制，可处理任意长度的整数。
            -- 边界：最高位之外仍可能多出一个进位位；输入须已去除前导零。
            local function add_positive(left, right)
              local result = {}
              local index = 1
              -- 从两个数的最低位（字符串末尾）开始对齐。
              local left_index = string.len(left)
              local right_index = string.len(right)
              local carry = 0
              -- 任一数还有未处理位或存在进位时继续循环。
              while left_index > 0 or right_index > 0 or carry > 0 do
                local sum = digit(left, left_index) + digit(right, right_index) + carry
                -- 当前位结果：取模后加 48 转为 ASCII 数字字符。
                result[index] = string.char((sum % 10) + 48)
                -- 进位只可能是 0 或 1（两个数字加进位最大为 19）。
                carry = math.floor(sum / 10)
                index = index + 1
                left_index = left_index - 1
                right_index = right_index - 1
              end
              -- 结果为低位在前，反转为正常书写顺序后返回。
              return reverse(result)
            end

            -- 无符号十进制减法（高精度核心段）。
            -- 意图：从最低位开始逐位相减，不够减时向高位借 1 当 10。
            -- 前置条件：left >= right（由调用方通过 compare_positive 保证），
            -- 因此最高位的借位必然被消化，不会产生负数结果。
            local function subtract_positive(left, right)
              local result = {}
              local index = 1
              local left_index = string.len(left)
              local right_index = string.len(right)
              local borrow = 0
              while left_index > 0 do
                -- 先扣除上一位的借位，再与右数对应位比较。
                local left_digit = digit(left, left_index) - borrow
                local right_digit = digit(right, right_index)
                -- 当前位不够减时向高位借位。
                if left_digit < right_digit then
                  left_digit = left_digit + 10
                  borrow = 1
                else
                  borrow = 0
                end
                result[index] = string.char(left_digit - right_digit + 48)
                index = index + 1
                left_index = left_index - 1
                right_index = right_index - 1
              end
              -- 先去前导零再反转，保证输出规范化（如 '0' 而非 '00'）。
              return reverse(trim_zeroes(result))
            end

            -- 有符号十进制加法分发：按符号决定走加法还是减法，并确定结果符号。
            -- 意图：把符号运算归约为上面的无符号加减，保证核心段只处理非负数字串。
            -- 边界：两个操作数都可能带负号；结果为零时统一返回不带符号的 '0'。
            local function add_signed(left, right)
              local left_sign, left_value = split(left)
              local right_sign, right_value = split(right)
              -- 同号：绝对值相加，符号跟随原符号；和为 0 时不保留负号。
              if left_sign == right_sign then
                local value = add_positive(left_value, right_value)
                return left_sign < 0 and value ~= '0' and '-' .. value or value
              end
              -- 异号：比较绝对值大小，用大数减小数。
              local comparison = compare_positive(left_value, right_value)
              -- 边界：绝对值相等时相互抵消，结果为 '0'。
              if comparison == 0 then return '0' end
              -- 左数绝对值更大，结果符号跟随左数。
              if comparison > 0 then
                local value = subtract_positive(left_value, right_value)
                return left_sign < 0 and '-' .. value or value
              end
              -- 右数绝对值更大，结果符号跟随右数。
              local value = subtract_positive(right_value, left_value)
              return right_sign < 0 and '-' .. value or value
            end

            -- 校验十进制文本是否仍在 Long 范围内，防止写回后 Java 端解析溢出。
            -- 意图：脚本计算不受浮点限制，结果可能超出 Long，必须在写回前拦截。
            -- 边界：正负边界不对称（负数绝对值上限比正数大 1），需分开比较。
            local function within_long(value)
              local sign, magnitude = split(value)
              -- 负数边界：绝对值不得超过 2^63（即 9223372036854775808）。
              if sign < 0 then
                return compare_positive(magnitude, '9223372036854775808') <= 0
              end
              -- 正数边界：不得超过 2^63 - 1（即 9223372036854775807）。
              return compare_positive(magnitude, '9223372036854775807') <= 0
            end

            -- 读取当前计数器信封；键不存在时按 0 起步。
            local current = redis.call('GET', KEYS[1])
            local current_value = '0'
            if current then
              -- 信封必须是 schemaVersion=1 的 Long 类型，否则说明键被其他数据占用。
              if not string.match(current, '"schemaVersion":1,')
                  or not string.match(current, '"typeIdentifier":"java%.lang%.Long"') then
                return redis.error_reply('invalid cache counter envelope')
              end
              -- 从信封尾部提取整数值文本。
              current_value = string.match(current, '"value":(%-?%d+)}$')
              -- 值字段不是整数（如 1.5）时拒绝，防止静默截断。
              if not current_value then return redis.error_reply('invalid cache counter value') end
              -- 已存储值本身越界时不继续累加，直接报溢出。
              if not within_long(current_value) then return redis.error_reply('cache counter overflow') end
            end
            -- 字符串高精度加法：增量由调用方以十进制文本传入。
            local updated = add_signed(current_value, ARGV[1])
            -- 累加结果越界时显式失败，键保持原值不变。
            if not within_long(updated) then return redis.error_reply('cache counter overflow') end
            -- 已存在信封则沿用其模板，否则使用调用方传入的初始信封模板。
            local template = current or ARGV[2]
            -- 只替换尾部 value 数值，信封其余字段保持不变。
            local encoded = string.gsub(
                template, '"value":%-?%d+}$', '"value":' .. updated .. '}', 1)
            -- 原子写回更新后的信封。
            redis.call('SET', KEYS[1], encoded)
            -- ARGV[3] 为 '0' 表示永久有效，非 0 时按毫秒刷新过期时间。
            if ARGV[3] ~= '0' then redis.call('PEXPIRE', KEYS[1], ARGV[3]) end
            -- 返回累加后的十进制文本，由 Java 端解析为 long。
            return updated
            """;

    /** 原子递增脚本。 */
    static final RedisScript<String> INCREMENT = RedisScript.of(INCREMENT_LUA, String.class);

    /**
     * 禁止实例化脚本常量类。
     *
     * @author lvdaxianer@yeah.net
     * @date 2026-07-17
     */
    private RedisLuaScripts() {
    }
}

package io.github.lvdaxianer.dinghai.dsl.testapp;

import io.github.lvdaxianer.dinghai.dsl.core.DslRequest;
import io.github.lvdaxianer.dinghai.dsl.core.QueryColumn;
import io.github.lvdaxianer.dinghai.dsl.core.QueryResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证执行接口同时提供兼容数组结果和可读对象结果。
 */
@WebMvcTest(TestDslController.class)
class ReadableQueryControllerTest {

    private static final String REQUEST = """
            {
              "dslType":"relational-query",
              "version":1,
              "payload":{},
              "parameters":{}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DslQueryService service;

    @Test
    void executeReturnsReadableRecordsAndKeepsRowsCompatible() throws Exception {
        QueryResult result = new QueryResult(
                List.of(
                        new QueryColumn("event_date", "date"),
                        new QueryColumn("status", "string"),
                        new QueryColumn("severity", "integer")),
                List.of(Arrays.asList("2026-11-13", null, 2)), 1, false);
        when(service.execute(any(DslRequest.class), eq("trace-readable-001"))).thenReturn(result);

        mockMvc.perform(post("/api/test/dsl/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-readable-001")
                        .content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows[0][0]").value("2026-11-13"))
                .andExpect(jsonPath("$.data.records[0].event_date").value("2026-11-13"))
                .andExpect(jsonPath("$.data.records[0].status").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.records[0].severity").value(2));
    }
}

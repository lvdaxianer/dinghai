## Context

`ProviderFileReadCache` uses a set of key digests to suppress duplicate cache
fill submissions. A cache miss still returns a provider stream for every
caller. The existing test makes two reads sequentially but does not ensure the
first asynchronous fill has remained active, so it cannot reliably exercise
the duplicate-submission path.

## Goals / Non-Goals

**Goals:**

- Make the in-flight cache-fill test independent of thread scheduling speed.
- Keep the established cache behavior under test unchanged.

**Non-Goals:**

- Change cache production code, public APIs, resource configuration, or logs.

## Decisions

Extend the test-only `CountingStorage` with a controllable blocking stream for
the second provider `get` call, which is the asynchronous fill stream. The
test will start the first miss, wait until the fill starts reading, perform the
second miss, and assert three provider reads: first passthrough, one fill, and
second passthrough. It then releases the stream and waits for the executor.

Bounded waits and a `finally` block will release the stream and close the
cache, preventing an assertion failure from stranding the daemon worker.

Lowering the assertion to two calls would hide the intended in-flight behavior.
Changing production code would alter a correct runtime path only to accommodate
an invalid test precondition.

## Risks / Trade-offs

- [A test stall from a missed release] → use bounded waits and always release
  the latch in `finally`.
- [Accidentally blocking a passthrough stream] → block only the known second
  provider `get`, which is the fill task after the first miss starts it.

## Reliability And Logging Review

This changes only an in-memory test double. It does not alter backend data,
external calls, production concurrency control, configuration, authorization,
errors, or logging. The deterministic latches cover the existing concurrency
contract without introducing production blocking behavior.

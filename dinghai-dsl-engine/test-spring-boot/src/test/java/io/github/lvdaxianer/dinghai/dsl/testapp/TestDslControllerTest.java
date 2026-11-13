package io.github.lvdaxianer.dinghai.dsl.testapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 HTTP 契约只接受结构化 DSL，并通过服务端上下文应用计划扩展。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/unused",
        "spring.datasource.username=test",
        "spring.datasource.password=",
        "spring.datasource.hikari.initialization-fail-timeout=-1"
})
@AutoConfigureMockMvc
class TestDslControllerTest {

    private static final String VALID_REQUEST = """
            {
              "dslType":"relational-query",
              "version":1,
              "payload":{
                "from":{"table":"fact_wireless_outage_timeline","alias":"f"},
                "select":[{"field":"f.event_date"}],
                "where":[],
                "joins":[],
                "groupBy":[],
                "orderBy":[],
                "limit":10
              },
              "parameters":{}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validatesDslWithoutReturningSql() throws Exception {
        mockMvc.perform(post("/api/test/dsl/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace_validate_001")
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId").value("trace_validate_001"))
                .andExpect(jsonPath("$.data.planType").value("relational-query"))
                .andExpect(jsonPath("$.data.metadata.appliedPlanExtensions[0]")
                        .value("test-active-domain-scope"))
                .andExpect(jsonPath("$.data.sql").doesNotExist());
    }

    @Test
    void explainsPlanWithoutAccessingDatabase() throws Exception {
        mockMvc.perform(post("/api/test/dsl/explain")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metadata.conditionCount").value(2));
    }

    @Test
    void rejectsRawSqlInPayload() throws Exception {
        mockMvc.perform(post("/api/test/dsl/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dslType":"relational-query","version":1,
                                 "payload":{"sql":"select 1"},"parameters":{}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
    }

    @Test
    void rejectsClientSelectedAdapterAndIdentityFields() throws Exception {
        mockMvc.perform(post("/api/test/dsl/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replaceFirst("\\{", "{\"adapter\":\"postgresql\",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));

        mockMvc.perform(post("/api/test/dsl/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replaceFirst("\\{", "{\"userId\":\"forged\",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_INVALID"));
    }
}

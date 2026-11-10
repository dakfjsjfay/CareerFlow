package com.team.resume.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理回归测试：
 * 请求体格式错误 / 非 UTF-8 编码返回 400；不存在的路径返回 404，而不是 500。
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new BodyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void nonUtf8BodyReturns400() throws Exception {
        // 0xB5 是 Windows 终端把中文转成 GBK 后常见的首字节，模拟非法 UTF-8 请求体
        byte[] body = {'{', '"', 'n', 'a', 'm', 'e', '"', ':', '"', (byte) 0xB5, '"', '}'};

        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void missingResourceReturns404() {
        ResponseEntity<Result<Void>> response = new GlobalExceptionHandler()
                .handleNotFound(new NoResourceFoundException(HttpMethod.GET, "/api/resumes/"));

        assertEquals(404, response.getStatusCode().value());
        assertEquals(404, response.getBody().getCode());
    }

    @RestController
    static class BodyController {

        @PostMapping("/test/body")
        public Result<Void> body(@RequestBody Payload payload) {
            return Result.ok();
        }
    }

    record Payload(String name) {
    }
}

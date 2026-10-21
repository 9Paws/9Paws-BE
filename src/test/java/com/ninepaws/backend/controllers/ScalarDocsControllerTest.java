package com.ninepaws.backend.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ScalarDocsControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ScalarDocsController controller = new ScalarDocsController("/v3/api-docs");
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  @DisplayName("GET /docs returns 200 OK with HTML containing Scalar CDN and OpenAPI URL")
  void renderScalarDocs_returns200AndHtml() throws Exception {
    mockMvc
        .perform(get("/docs"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(
            content().string(containsString("https://cdn.jsdelivr.net/npm/@scalar/api-reference")))
        .andExpect(content().string(containsString("url: '/v3/api-docs'")))
        .andExpect(content().string(containsString("NinePaws API Reference")));
  }

  @Test
  @DisplayName("GET /docs/ with trailing slash returns 200 OK")
  void renderScalarDocs_trailingSlash_returns200() throws Exception {
    mockMvc
        .perform(get("/docs/"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }
}

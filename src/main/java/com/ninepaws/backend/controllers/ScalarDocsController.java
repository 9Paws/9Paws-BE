package com.ninepaws.backend.controllers;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Hidden
@Controller
@ConditionalOnProperty(name = "app.scalar.enabled", havingValue = "true", matchIfMissing = true)
public class ScalarDocsController {

  private final String openApiUrl;

  public ScalarDocsController(@Value("${app.scalar.url:/v3/api-docs}") String openApiUrl) {
    this.openApiUrl = openApiUrl;
  }

  @GetMapping(
      value = {"${app.scalar.path:/docs}", "${app.scalar.path:/docs}/"},
      produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public ResponseEntity<String> renderScalarDocs() {
    String html =
        """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <title>NinePaws API Reference</title>
            <link rel="icon" type="image/svg+xml" href="https://scalar.com/favicon.svg" />
            <style>
              body {
                margin: 0;
                padding: 0;
              }
            </style>
          </head>
          <body>
            <div id="app"></div>
            <script type="module">
              import { createApiReference } from 'https://cdn.jsdelivr.net/npm/@scalar/api-reference/esm.js';

              createApiReference('#app', {
                url: '%s',
                theme: 'purple',
                showSidebar: true
              });
            </script>
          </body>
        </html>
        """
            .formatted(openApiUrl);

    return ResponseEntity.ok(html);
  }
}

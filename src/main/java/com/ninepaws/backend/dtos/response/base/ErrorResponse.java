package com.ninepaws.backend.dtos.response.base;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonPropertyOrder({"httpStatus", "message", "errorCode", "timestamp", "path"})
public class ErrorResponse extends AbstractBaseResponse {
  private String errorCode;
  private Instant timestamp;
  private String path;
}

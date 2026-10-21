package com.ninepaws.backend.dtos.response.base;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import java.util.Map;
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
@JsonPropertyOrder({"httpStatus", "message", "errorCode", "timestamp", "path", "items"})
public class DetailedErrorResponse extends ErrorResponse {
  private Map<String, List<String>> items;
}

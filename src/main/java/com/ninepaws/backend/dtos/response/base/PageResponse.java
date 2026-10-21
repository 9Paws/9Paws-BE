package com.ninepaws.backend.dtos.response.base;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"items", "page", "size", "totalElements", "totalPages", "last"})
public class PageResponse<T> {
  private List<T> items;
  private int page;
  private int size;
  private long totalElements;
  private int totalPages;
  private boolean last;

  public static <T> PageResponse<T> from(Page<T> pageData) {
    return PageResponse.<T>builder()
        .items(pageData.getContent())
        .page(pageData.getNumber())
        .size(pageData.getSize())
        .totalElements(pageData.getTotalElements())
        .totalPages(pageData.getTotalPages())
        .last(pageData.isLast())
        .build();
  }
}

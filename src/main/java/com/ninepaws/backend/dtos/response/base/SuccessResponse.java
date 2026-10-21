package com.ninepaws.backend.dtos.response.base;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@JsonPropertyOrder({"httpStatus", "message"})
public class SuccessResponse extends AbstractBaseResponse {}

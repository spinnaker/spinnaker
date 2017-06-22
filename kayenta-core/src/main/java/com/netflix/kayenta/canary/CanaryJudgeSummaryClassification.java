package com.netflix.kayenta.canary;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import javax.validation.constraints.NotNull;

@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanaryJudgeSummaryClassification {

  @NotNull
  @Getter
  private String name;

  @Getter
  private int count;
}

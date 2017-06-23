package com.netflix.kayenta.canary.results;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CanaryAnalysisResult {

  // Todo: (mgraff) How to describe pre- and post-filter results?
  // Todo: (mgraff) Add a place to return additional timeseries data
  // Todo: (mgraff) Add a way to describe graph annotations on canary, baseline, and additional TS

  @NotNull
  @Getter
  private String name;

  @NotNull
  @Getter
  private Map<String, String> tags;

  @NotNull
  @Getter
  private CanaryJudgeScore score;

  @NotNull
  @Getter
  private List<String> groups;

  @NotNull
  @Getter
  private Map<String, String> experimentMetrics;

  @NotNull
  @Getter
  private Map<String, String> controlMetrics;

  @NotNull
  @Getter
  private Map<String, String> resultMetrics;
}

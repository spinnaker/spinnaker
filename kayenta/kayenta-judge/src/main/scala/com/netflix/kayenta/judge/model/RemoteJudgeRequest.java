package com.netflix.kayenta.judge.model;

import com.netflix.kayenta.canary.CanaryClassifierThresholdsConfig;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.metrics.MetricSetPair;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Builder
public class RemoteJudgeRequest {

  @NotNull @Getter private CanaryConfig canaryConfig;

  @NotNull @Getter private CanaryClassifierThresholdsConfig scoreThresholds;

  @NotNull @Getter private List<MetricSetPair> metricSetPairList;
}

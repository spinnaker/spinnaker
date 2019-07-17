package com.netflix.kayenta.judge.model;

import com.netflix.kayenta.canary.CanaryClassifierThresholdsConfig;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.metrics.MetricSetPair;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

@Builder
public class RemoteJudgeRequest {

  @NotNull @Getter private CanaryConfig canaryConfig;

  @NotNull @Getter private CanaryClassifierThresholdsConfig scoreThresholds;

  @NotNull @Getter private List<MetricSetPair> metricSetPairList;
}

package com.netflix.kayenta.canary.providers;

import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class DatadogCanaryMetricSetQueryConfig implements CanaryMetricSetQueryConfig {
  @NotNull
  @Getter
  private String metricName;

  @Override
  public String getServiceType() {
    return "datadog";
  }
}

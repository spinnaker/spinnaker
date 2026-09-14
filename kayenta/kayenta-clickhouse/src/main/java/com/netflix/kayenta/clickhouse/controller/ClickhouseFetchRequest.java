package com.netflix.kayenta.clickhouse.controller;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request body for using the Clickhouse Fetch controller")
class ClickhouseFetchRequest {

  @NotNull
  @Schema(description = "The metric config to query Clickhouse for")
  CanaryMetricConfig canaryMetricConfig;

  @NotNull @Builder.Default Map<String, String> extendedScopeParams = new HashMap<>();

  @Nullable CanaryConfig canaryConfig;
}

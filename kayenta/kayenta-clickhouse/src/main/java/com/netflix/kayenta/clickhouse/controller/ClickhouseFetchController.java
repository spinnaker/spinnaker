package com.netflix.kayenta.clickhouse.controller;

import static com.netflix.kayenta.canary.util.FetchControllerUtils.determineDefaultProperty;

import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.clickhouse.config.ClickhouseConfigurationTestControllerDefaultProperties;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lets the Deck UI run an ad-hoc Clickhouse metric query without creating a full canary run. */
@RestController
@RequestMapping("/fetch/clickhouse")
@Slf4j
public class ClickhouseFetchController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final SynchronousQueryProcessor synchronousQueryProcessor;
  private final ClickhouseConfigurationTestControllerDefaultProperties
      clickhouseConfigurationTestControllerDefaultProperties;

  @Autowired
  public ClickhouseFetchController(
      AccountCredentialsRepository accountCredentialsRepository,
      SynchronousQueryProcessor synchronousQueryProcessor,
      ClickhouseConfigurationTestControllerDefaultProperties
          clickhouseConfigurationTestControllerDefaultProperties) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
    this.clickhouseConfigurationTestControllerDefaultProperties =
        clickhouseConfigurationTestControllerDefaultProperties;
  }

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public Map queryMetrics(
      @RequestParam(required = false) final String metricsAccountName,
      @RequestParam(required = false) final String storageAccountName,
      @Parameter(required = true) @Valid @RequestBody ClickhouseFetchRequest clickhouseFetchRequest,
      @Parameter(description = "The scope of the Clickhouse query.") @RequestParam(required = false)
          String scope,
      @Parameter(description = "The location of the Clickhouse query.")
          @RequestParam(required = false)
          String location,
      @Parameter(description = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z") @RequestParam
          String start,
      @Parameter(description = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z") @RequestParam
          String end,
      @Parameter(example = "60", description = "seconds") @RequestParam Long step,
      @Parameter(schema = @Schema(defaultValue = "0"), description = "canary config metrics index")
          @RequestParam(required = false)
          Integer metricIndex,
      @Parameter(schema = @Schema(defaultValue = "false")) @RequestParam(required = false)
          final boolean dryRun)
      throws IOException {

    // Apply defaults.
    scope =
        determineDefaultProperty(
            scope, "scope", clickhouseConfigurationTestControllerDefaultProperties);
    start =
        determineDefaultProperty(
            start, "start", clickhouseConfigurationTestControllerDefaultProperties);
    end =
        determineDefaultProperty(
            end, "end", clickhouseConfigurationTestControllerDefaultProperties);

    if (StringUtils.isEmpty(start)) {
      throw new IllegalArgumentException("Start time is required.");
    }

    if (StringUtils.isEmpty(end)) {
      throw new IllegalArgumentException("End time is required.");
    }

    String resolvedMetricsAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(metricsAccountName, AccountCredentials.Type.METRICS_STORE)
            .getName();
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();

    CanaryScope canaryScope =
        CanaryScope.builder()
            .scope(scope)
            .location(location)
            .start(Instant.parse(start))
            .end(Instant.parse(end))
            .step(step)
            .extendedScopeParams(clickhouseFetchRequest.extendedScopeParams)
            .build();

    return synchronousQueryProcessor.processQueryAndReturnMap(
        resolvedMetricsAccountName,
        resolvedStorageAccountName,
        clickhouseFetchRequest.getCanaryConfig(),
        clickhouseFetchRequest.getCanaryMetricConfig(),
        Optional.ofNullable(metricIndex).orElse(0),
        canaryScope,
        dryRun);
  }
}

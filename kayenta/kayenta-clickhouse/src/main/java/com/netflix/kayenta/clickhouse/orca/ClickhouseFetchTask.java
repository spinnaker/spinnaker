package com.netflix.kayenta.clickhouse.orca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ClickhouseFetchTask implements RetryableTask {

  private final ObjectMapper kayentaObjectMapper;
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final SynchronousQueryProcessor synchronousQueryProcessor;

  @Autowired
  public ClickhouseFetchTask(
      ObjectMapper kayentaObjectMapper,
      AccountCredentialsRepository accountCredentialsRepository,
      SynchronousQueryProcessor synchronousQueryProcessor) {
    this.kayentaObjectMapper = kayentaObjectMapper;
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
  }

  @Override
  public long getBackoffPeriod() {
    return Duration.ofSeconds(2).toMillis();
  }

  @Override
  public long getTimeout() {
    return Duration.ofMinutes(2).toMillis();
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    Map<String, Object> context = stage.getContext();
    String metricsAccountName = (String) context.get("metricsAccountName");
    String storageAccountName = (String) context.get("storageAccountName");
    Map<String, Object> canaryConfigMap = (Map<String, Object>) context.get("canaryConfig");
    CanaryConfig canaryConfig =
        kayentaObjectMapper.convertValue(canaryConfigMap, CanaryConfig.class);
    int metricIndex = (Integer) stage.getContext().get("metricIndex");
    CanaryScope canaryScope;
    try {
      canaryScope =
          kayentaObjectMapper.readValue(
              (String) stage.getContext().get("canaryScope"), CanaryScope.class);
    } catch (IOException e) {
      log.warn("Unable to parse JSON scope", e);
      throw new RuntimeException(e);
    }
    String resolvedMetricsAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(metricsAccountName, AccountCredentials.Type.METRICS_STORE)
            .getName();
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();

    return synchronousQueryProcessor.executeQueryAndProduceTaskResult(
        resolvedMetricsAccountName,
        resolvedStorageAccountName,
        canaryConfig,
        metricIndex,
        canaryScope);
  }
}

package com.netflix.kayenta.clickhouse.canary;

import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopeFactory;
import org.springframework.stereotype.Component;

/**
 * Clickhouse queries are always user-authored SQL templates (see {@link
 * com.netflix.kayenta.canary.providers.metrics.ClickhouseCanaryMetricSetQueryConfig}), so there is
 * no provider-specific scope data to attach - the scope is passed through unmodified and the
 * template author references {@code ${scope}}/{@code ${location}} directly in their SQL.
 */
@Component
public class ClickhouseCanaryScopeFactory implements CanaryScopeFactory {

  public static final String SERVICE_TYPE = "clickhouse";

  @Override
  public boolean handles(String serviceType) {
    return SERVICE_TYPE.equalsIgnoreCase(serviceType);
  }

  @Override
  public CanaryScope buildCanaryScope(CanaryScope scope) {
    return scope;
  }
}

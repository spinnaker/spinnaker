package com.netflix.kayenta.datadog.canary;

import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.CanaryScopeFactory;
import org.springframework.stereotype.Component;

@Component
public class DatadogCanaryScopeFactory implements CanaryScopeFactory {
  @Override
  public boolean handles(String serviceType) {
    return serviceType.equals("datadog");
  }

  @Override
  public CanaryScope buildCanaryScope(CanaryScope scope) {
    return scope;
  }
}

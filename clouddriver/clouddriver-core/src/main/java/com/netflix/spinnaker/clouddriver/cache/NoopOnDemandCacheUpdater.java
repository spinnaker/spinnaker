package com.netflix.spinnaker.clouddriver.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/** A default, no-op implementation of an {@link OnDemandCacheUpdater} */
@Component
public class NoopOnDemandCacheUpdater implements OnDemandCacheUpdater {
  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return false;
  }

  @Override
  public OnDemandCacheResult handle(OnDemandType type, String cloudProvider, Map<String, ?> data) {
    return new OnDemandCacheResult(OnDemandCacheStatus.SUCCESSFUL);
  }

  @Override
  public Collection<Map<String, Object>> pendingOnDemandRequests(
      OnDemandType type, String cloudProvider) {
    return new ArrayList<>();
  }

  @Override
  public Map<String, Object> pendingOnDemandRequest(
      OnDemandType type, String cloudProvider, String id) {
    return null;
  }
}

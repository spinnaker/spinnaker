package com.netflix.spinnaker.kork.tomcat.x509;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BlocklistEnabledDynamicConfigMonitor {
  private final DynamicConfigService dynamicConfigService;

  public BlocklistEnabledDynamicConfigMonitor(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService;
    syncEnabledProperty();
  }

  @Scheduled(fixedRate = 5000L)
  public void syncEnabledProperty() {
    BlocklistingX509TrustManager.BLOCKLIST_ENABLED.set(
        dynamicConfigService.isEnabled("ssl.blocklist", true));
  }
}

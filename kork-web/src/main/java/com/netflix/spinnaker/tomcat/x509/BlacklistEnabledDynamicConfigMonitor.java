package com.netflix.spinnaker.tomcat.x509;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BlacklistEnabledDynamicConfigMonitor {
  private final DynamicConfigService dynamicConfigService;

  @Autowired
  public BlacklistEnabledDynamicConfigMonitor(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService;
    syncEnabledProperty();
  }

  @Scheduled(fixedRate = 5000L)
  public void syncEnabledProperty() {
    BlacklistingX509TrustManager.BLACKLIST_ENABLED.set(
        dynamicConfigService.isEnabled("ssl.blacklist", true));
  }
}

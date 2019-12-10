package com.netflix.spinnaker.gate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("slack")
public class SlackConfigProperties {
  String token;
  String baseUrl;
  Long channelRefreshIntervalMillis;
}

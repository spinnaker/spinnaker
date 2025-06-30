package com.netflix.spinnaker.config.yaml;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spinnaker.yaml")
@Data
public class YamlParserProperties {
  private int maxAliasesForCollections;
  private int codePointLimit;
}

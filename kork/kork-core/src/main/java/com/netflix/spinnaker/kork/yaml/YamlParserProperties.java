package com.netflix.spinnaker.kork.yaml;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spinnaker.yaml")
@Data
public class YamlParserProperties {
  private Integer maxAliasesForCollections;
  private Integer codePointLimit;
}

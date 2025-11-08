package com.netflix.spinnaker.kork.yaml;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snakeyaml")
@Data
public class YamlParserProperties {
  private Integer maxAliasesForCollections;
  private Integer codePointLimit;
}

package com.netflix.spinnaker.kork.yaml;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snakeyaml")
@Data
public class YamlParserProperties {
  private Integer maxAliasesForCollections;
  private Integer codePointLimit;

  /**
   * Read plain YAML scalars like Jackson 2 did (YAML 1.1: yes/no/on/off, True, ~, 0644, 0xFF,
   * 1_000) rather than Jackson 3's YAML 1.2 rules. Defaults to true; applies to {@link
   * YamlHelper#yamlFactory()}.
   */
  private Boolean yaml11Scalars;
}

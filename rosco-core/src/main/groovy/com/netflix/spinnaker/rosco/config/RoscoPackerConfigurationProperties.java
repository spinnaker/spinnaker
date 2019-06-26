package com.netflix.spinnaker.rosco.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("packer")
@Data
public class RoscoPackerConfigurationProperties {
  Boolean timestamp;
  List<String> additionalParameters = new ArrayList<>();
}

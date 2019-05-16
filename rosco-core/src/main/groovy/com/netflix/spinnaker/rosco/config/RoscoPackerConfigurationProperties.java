package com.netflix.spinnaker.rosco.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("packer")
@Data
public class RoscoPackerConfigurationProperties {
  Boolean timestamp;
  List<String> additionalParameters = new ArrayList<>();
}

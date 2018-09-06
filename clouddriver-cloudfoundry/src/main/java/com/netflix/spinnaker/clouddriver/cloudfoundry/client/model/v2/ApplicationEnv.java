package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ApplicationEnv {
  private SystemEnv systemEnvJson;

  @Data
  public static class SystemEnv {
    @JsonProperty("VCAP_SERVICES")
    private Map<String, List<ServiceInstanceInfo>> vcapServices;
  }
}

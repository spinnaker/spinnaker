package com.netflix.spinnaker.clouddriver.cloudrun.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.run.v1.model.Revision;
import com.google.api.services.run.v1.model.Service;
import com.netflix.spinnaker.clouddriver.model.Health;
import com.netflix.spinnaker.clouddriver.model.HealthState;
import java.util.Map;
import lombok.Data;

@Data
public class CloudrunHealth implements Health {
  private HealthState state;
  private String source;
  private String type;

  public CloudrunHealth(Revision version, Service service) {
    // cloudrun instance data is not kept available. This is a dummy info just to fill in
    source = "Service ";
    type = "Cloudrun Service";
    state = HealthState.Up;
  }

  public Map<String, Object> toMap() {
    return new ObjectMapper().convertValue(this, new TypeReference<Map<String, Object>>() {});
  }
}

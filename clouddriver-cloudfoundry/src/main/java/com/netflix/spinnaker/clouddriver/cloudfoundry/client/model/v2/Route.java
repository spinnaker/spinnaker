package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Route {
  private String host;
  private String path;
  @Nullable private Integer port;
  private String domainGuid;
  private String spaceGuid;
}

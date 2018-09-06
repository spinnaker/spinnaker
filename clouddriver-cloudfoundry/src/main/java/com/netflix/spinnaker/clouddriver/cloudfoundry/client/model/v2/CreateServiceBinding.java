package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class CreateServiceBinding {
  private final String serviceInstanceGuid;
  private final String appGuid;
}

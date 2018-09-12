package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;

@RequiredArgsConstructor
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScaleApplication {
  @Nullable
  private final Integer instances;

  @Nullable
  private final Integer memoryInMb;

  @Nullable
  private final Integer diskInMb;
}

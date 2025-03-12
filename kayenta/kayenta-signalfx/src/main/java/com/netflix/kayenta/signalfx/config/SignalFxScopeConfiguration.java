package com.netflix.kayenta.signalfx.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalFxScopeConfiguration {

  private String defaultScopeKey;
  private String defaultLocationKey;
}

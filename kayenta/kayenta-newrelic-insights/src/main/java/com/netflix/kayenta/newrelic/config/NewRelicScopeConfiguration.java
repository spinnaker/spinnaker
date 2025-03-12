package com.netflix.kayenta.newrelic.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewRelicScopeConfiguration {

  private String defaultScopeKey;
  private String defaultLocationKey;
}

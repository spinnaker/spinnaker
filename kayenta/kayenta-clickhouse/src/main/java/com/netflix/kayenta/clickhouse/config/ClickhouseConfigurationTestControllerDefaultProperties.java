package com.netflix.kayenta.clickhouse.config;

import lombok.Getter;
import lombok.Setter;

/** Allows operators to specify default values for the Clickhouse Fetch Controller. */
public class ClickhouseConfigurationTestControllerDefaultProperties {

  @Getter @Setter private String scope;

  @Getter @Setter private String start;

  @Getter @Setter private String end;
}

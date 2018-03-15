package com.netflix.kayenta.datadog.config;

import lombok.Getter;
import lombok.Setter;

/**
 * This configuration class allows you to specify default values for the PrometheusFetchController.
 */
public class DatadogConfigurationTestControllerDefaultProperties {

    @Getter
    @Setter
    private String scope;

    @Getter
    @Setter
    private String start;

    @Getter
    @Setter
    private String end;
}

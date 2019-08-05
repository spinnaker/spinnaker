package com.netflix.spinnaker.igor.build.model;

import lombok.Data;

@Data
public class GenericParameterDefinition implements ParameterDefinition {
  private String name;
  private String defaultValue;
  private String description = "";

  public GenericParameterDefinition(String name, String defaultValue) {
    this.name = name;
    this.defaultValue = defaultValue;
  }

  public GenericParameterDefinition(String name, String defaultValue, String description) {
    this(name, defaultValue);
    this.description = description;
  }
}

package com.netflix.spinnaker.igor.build.model;

import java.util.List;
import lombok.Data;

@Data
public class GenericJobConfiguration implements JobConfiguration {
  private String description;
  private String displayName;
  private String name;
  private boolean buildable;
  private String url;
  private boolean concurrentBuild;
  private List<GenericParameterDefinition> parameterDefinitionList;

  public GenericJobConfiguration(
      String description,
      String displayName,
      String name,
      boolean buildable,
      String url,
      boolean concurrentBuild,
      List<GenericParameterDefinition> genericParameterDefinition) {
    this.description = description;
    this.displayName = displayName;
    this.name = name;
    this.buildable = buildable;
    this.url = url;
    this.concurrentBuild = concurrentBuild;
    this.parameterDefinitionList = genericParameterDefinition;
  }

  public GenericJobConfiguration(String description, String name) {
    this.description = description;
    this.name = name;
  }
}

package com.netflix.spinnaker.igor.jenkins.client.model;

import org.simpleframework.xml.Default;

/** Represents either an upstream or downstream dependency in Jenkins */
@Default
public class BuildDependency {
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  private String name;
  private String url;
}

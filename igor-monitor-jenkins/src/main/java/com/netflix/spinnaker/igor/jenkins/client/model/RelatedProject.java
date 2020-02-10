package com.netflix.spinnaker.igor.jenkins.client.model;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

/** Represents a upstream/downstream project for a Jenkins job */
@Root(strict = false)
public class RelatedProject {
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

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  @Element private String name;
  @Element private String url;
  @Element private String color;
}

package com.netflix.spinnaker.igor.jenkins.client.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.netflix.spinnaker.igor.build.model.JobConfiguration;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/** Represents the basic Jenkins job configuration information */
@XmlRootElement
public class JobConfig implements JobConfiguration {
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean getBuildable() {
    return buildable;
  }

  public boolean isBuildable() {
    return buildable;
  }

  public void setBuildable(boolean buildable) {
    this.buildable = buildable;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public List<ParameterDefinition> getParameterDefinitionList() {
    return parameterDefinitionList;
  }

  public void setParameterDefinitionList(List<ParameterDefinition> parameterDefinitionList) {
    this.parameterDefinitionList = parameterDefinitionList;
  }

  public List<UpstreamProject> getUpstreamProjectList() {
    return upstreamProjectList;
  }

  public void setUpstreamProjectList(List<UpstreamProject> upstreamProjectList) {
    this.upstreamProjectList = upstreamProjectList;
  }

  public List<DownstreamProject> getDownstreamProjectList() {
    return downstreamProjectList;
  }

  public void setDownstreamProjectList(List<DownstreamProject> downstreamProjectList) {
    this.downstreamProjectList = downstreamProjectList;
  }

  public boolean getConcurrentBuild() {
    return concurrentBuild;
  }

  public boolean isConcurrentBuild() {
    return concurrentBuild;
  }

  public void setConcurrentBuild(boolean concurrentBuild) {
    this.concurrentBuild = concurrentBuild;
  }

  @XmlElement(required = false)
  private String description;

  @XmlElement private String displayName;
  @XmlElement private String name;
  @XmlElement private boolean buildable;
  @XmlElement private String color;
  @XmlElement private String url;

  @XmlElementWrapper(name = "property")
  @XmlElement(name = "parameterDefinition", required = false)
  private List<ParameterDefinition> parameterDefinitionList;

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "upstreamProject", required = false)
  private List<UpstreamProject> upstreamProjectList;

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "downstreamProject", required = false)
  private List<DownstreamProject> downstreamProjectList;

  @XmlElement private boolean concurrentBuild;
}

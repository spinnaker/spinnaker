package com.netflix.spinnaker.igor.jenkins.client.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/** Captures build dependencies for a jenkins job */
@XmlRootElement
public class BuildDependencies {
  public List<BuildDependency> getDownstreamProjects() {
    return downstreamProjects;
  }

  public void setDownstreamProjects(List<BuildDependency> downstreamProjects) {
    this.downstreamProjects = downstreamProjects;
  }

  public List<BuildDependency> getUpstreamProjects() {
    return upstreamProjects;
  }

  public void setUpstreamProjects(List<BuildDependency> upstreamProjects) {
    this.upstreamProjects = upstreamProjects;
  }

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "downstreamProject", required = false)
  private List<BuildDependency> downstreamProjects;

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "upstreamProject", required = false)
  private List<BuildDependency> upstreamProjects;
}

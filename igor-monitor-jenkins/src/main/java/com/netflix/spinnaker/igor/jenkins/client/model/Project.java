package com.netflix.spinnaker.igor.jenkins.client.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;

/** Represents a Project returned by the Jenkins service in the project list */
public class Project {
  public List<Project> getList() {
    return list;
  }

  public void setList(List<Project> list) {
    this.list = list;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Build getLastBuild() {
    return lastBuild;
  }

  public void setLastBuild(Build lastBuild) {
    this.lastBuild = lastBuild;
  }

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "job", required = false)
  private List<Project> list;

  @XmlElement private String name;
  @XmlElement private Build lastBuild;
}

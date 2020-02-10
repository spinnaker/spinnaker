package com.netflix.spinnaker.igor.jenkins.client.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/** Represents a list of builds */
@XmlRootElement
public class BuildsList {
  public List<Build> getList() {
    return list;
  }

  public void setList(List<Build> list) {
    this.list = list;
  }

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "build")
  private List<Build> list;
}

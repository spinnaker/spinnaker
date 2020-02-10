package com.netflix.spinnaker.igor.jenkins.client.model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/** Represents a list of projects */
@XmlRootElement(name = "hudson")
public class JobList {
  public List<Job> getList() {
    return list;
  }

  public void setList(List<Job> list) {
    this.list = list;
  }

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "job")
  private List<Job> list;
}

package com.netflix.spinnaker.igor.jenkins.client.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class QueuedJob {
  @XmlElement(name = "number")
  public Integer getNumber() {
    final QueuedExecutable executable1 = executable;
    return (executable1 == null ? null : executable1.getNumber());
  }

  public QueuedExecutable getExecutable() {
    return executable;
  }

  public void setExecutable(QueuedExecutable executable) {
    this.executable = executable;
  }

  @XmlElement private QueuedExecutable executable;
}

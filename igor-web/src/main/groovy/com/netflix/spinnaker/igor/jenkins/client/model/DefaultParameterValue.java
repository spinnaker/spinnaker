package com.netflix.spinnaker.igor.jenkins.client.model;

import javax.xml.bind.annotation.XmlElement;

public class DefaultParameterValue {
  @XmlElement public String name;

  @XmlElement public String value;

  public String getName() {
    return name;
  }

  public String getValue() {
    return value;
  }
}

package com.netflix.spinnaker.igor.jenkins.client.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/** Represents a Jenkins CSRF Crumb. */
@XmlRootElement
public class Crumb {
  public String getCrumbRequestField() {
    return crumbRequestField;
  }

  public void setCrumbRequestField(String crumbRequestField) {
    this.crumbRequestField = crumbRequestField;
  }

  public String getCrumb() {
    return crumb;
  }

  public void setCrumb(String crumb) {
    this.crumb = crumb;
  }

  @XmlElement private String crumbRequestField;
  @XmlElement private String crumb;
}

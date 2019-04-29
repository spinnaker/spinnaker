package com.netflix.spinnaker.igor.jenkins.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/** Represents a parameter for a Jenkins job */
@XmlType(
    propOrder = {
      "defaultParameterValue",
      "name",
      "description",
      "type",
      "choice",
      "defaultValue",
      "defaultName"
    })
public class ParameterDefinition {
  @XmlElement public DefaultParameterValue defaultParameterValue;

  @XmlElement public String name;

  @XmlElement(required = false)
  public String description;

  @XmlElement public String type;

  @JacksonXmlElementWrapper(useWrapping = false)
  @XmlElement(name = "choice", required = false)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public List<String> choices;

  @XmlElement(name = "defaultValue")
  public String getDefaultValue() {
    if (defaultParameterValue == null) {
      return null;
    }
    return defaultParameterValue.getValue();
  }

  @XmlElement(name = "defaultName")
  public String getDefaultName() {
    if (defaultParameterValue == null) {
      return null;
    }
    return defaultParameterValue.getName();
  }
}

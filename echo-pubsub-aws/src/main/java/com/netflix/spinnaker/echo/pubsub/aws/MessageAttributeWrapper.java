package com.netflix.spinnaker.echo.pubsub.aws;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MessageAttributeWrapper {

  @JsonProperty("Type")
  private String attributeType;

  @JsonProperty("Value")
  private String attributeValue;

  public MessageAttributeWrapper() {
  }

  public MessageAttributeWrapper(String attributeType, String attributeValue) {
    this.attributeType = attributeType;
    this.attributeValue= attributeValue;
  }
}

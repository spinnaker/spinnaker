package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model;

import lombok.Data;

@Data
public class Token {
  private String accessToken;

  /**
   * Dimensioned in seconds
   */
  private long expiresIn;

  /**
   * A globally unique identifier for this token
   */
  private String jti;
}

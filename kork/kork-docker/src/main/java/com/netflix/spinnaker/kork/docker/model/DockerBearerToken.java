package com.netflix.spinnaker.kork.docker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

@Data
@ToString(callSuper = false, includeFieldNames = true)
public class DockerBearerToken {
  private String token;

  @JsonProperty("access_token")
  private String accessToken;

  @JsonProperty("bearer_token")
  private String bearerToken;

  @JsonProperty("expires_in")
  private int expiresIn;

  @JsonProperty("issued_at")
  private String issuedAt;
}

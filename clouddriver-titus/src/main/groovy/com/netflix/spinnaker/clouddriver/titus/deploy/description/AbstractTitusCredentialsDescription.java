package com.netflix.spinnaker.clouddriver.titus.deploy.description;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;

public abstract class AbstractTitusCredentialsDescription implements CredentialsNameable {
  @JsonIgnore private NetflixTitusCredentials credentials;

  @JsonProperty("credentials")
  public String getCredentialAccount() {
    return this.credentials.getName();
  }

  public NetflixTitusCredentials getCredentials() {
    return credentials;
  }

  public void setCredentials(NetflixTitusCredentials credentials) {
    this.credentials = credentials;
  }
}

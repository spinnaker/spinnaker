package com.netflix.spinnaker.clouddriver.titus.deploy.description;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.security.resources.CredentialsNameable;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import java.util.Optional;

@JsonIgnoreProperties("credentials")
public abstract class AbstractTitusCredentialsDescription implements CredentialsNameable {

  private String account;

  private NetflixTitusCredentials credentials;

  @JsonIgnore
  public NetflixTitusCredentials getCredentials() {
    return credentials;
  }

  public void setCredentials(NetflixTitusCredentials credentials) {
    this.credentials = credentials;
  }

  /** For JSON serde only. */
  @JsonProperty
  public void setAccount(String account) {
    this.account = account;
  }

  /** For JSON serde only. */
  @JsonProperty
  @Override
  public String getAccount() {
    return Optional.ofNullable(this.credentials)
        .map(NetflixTitusCredentials::getName)
        .orElse(account);
  }
}

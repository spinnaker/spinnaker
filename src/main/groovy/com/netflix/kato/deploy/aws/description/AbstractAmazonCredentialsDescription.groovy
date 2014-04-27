package com.netflix.kato.deploy.aws.description

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.kato.deploy.DeployDescription
import com.netflix.kato.security.aws.AmazonCredentials

abstract class AbstractAmazonCredentialsDescription {
  @JsonIgnore
  AmazonCredentials credentials
  @JsonProperty("credentials")
  String getCredentialAccount() {
    this.credentials.environment
  }
}

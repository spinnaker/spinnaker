package com.netflix.kato.security.aws

import com.amazonaws.auth.AWSCredentialsProvider
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.kato.security.NamedAccountCredentials
import javax.xml.bind.annotation.XmlTransient
import org.springframework.data.annotation.Transient

class AmazonNamedAccountCredentials implements NamedAccountCredentials<AmazonCredentials> {
  @JsonIgnore
  @XmlTransient
  @Transient
  final AmazonCredentials credentials

  AmazonNamedAccountCredentials(AWSCredentialsProvider provider, String environment) {
    this.credentials = new AmazonCredentials(provider.credentials, environment)
  }
}

package com.netflix.kato.security.aws

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.kato.security.NamedAccountCredentials
import javax.xml.bind.annotation.XmlTransient
import org.springframework.data.annotation.Transient

class AmazonNamedAccountCredentials implements NamedAccountCredentials<AmazonCredentials> {
  @JsonIgnore
  @XmlTransient
  @Transient
  final AmazonCredentials credentials

  AmazonNamedAccountCredentials(String accessId, String secretKey, String environment) {
    this.credentials = new AmazonCredentials(accessId, secretKey, environment)
  }
}

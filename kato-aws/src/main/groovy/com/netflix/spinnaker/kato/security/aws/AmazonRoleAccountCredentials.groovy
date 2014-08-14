/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.kato.security.aws

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.kato.config.AwsRegion
import com.netflix.spinnaker.kato.security.NamedAccountCredentials
import org.springframework.data.annotation.Transient

import javax.xml.bind.annotation.XmlTransient

class AmazonRoleAccountCredentials implements NamedAccountCredentials<DiscoveryAwareAmazonCredentials> {
  private final AWSCredentialsProvider provider
  private final String accountId
  private final String environment
  private final String role
  private final String edda
  private final boolean discoveryEnabled
  private final List<AwsRegion> regions

  AmazonRoleAccountCredentials(AWSCredentialsProvider provider, String accountId, String environment, String role, String edda, boolean discoveryEnabled, List<AwsRegion> regions) {
    this.provider = provider
    this.accountId = accountId
    this.environment = environment
    this.role = role
    this.edda = edda
    this.discoveryEnabled = discoveryEnabled
    this.regions = regions
  }

  @JsonIgnore
  @XmlTransient
  @Transient
  public DiscoveryAwareAmazonCredentials getCredentials() {
    AWSCredentials credentials = new STSAssumeRoleSessionCredentialsProvider(provider, "arn:aws:iam::${accountId}:role/asgard", "Spinnaker")?.credentials
    new DiscoveryAwareAmazonCredentials(credentials, environment, edda, discoveryEnabled)
  }

  @JsonIgnore
  @XmlTransient
  @Transient
  public String getAccountId() {
    accountId
  }

  public List<AwsRegion> getRegions() {
    regions
  }
}

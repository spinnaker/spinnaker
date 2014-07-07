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




package com.netflix.spinnaker.front50.security.aws

import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.amazoncomponents.security.AmazonCredentials
import org.springframework.data.annotation.Transient

import javax.xml.bind.annotation.XmlTransient

class AmazonRoleAccountCredentials extends AbstractAmazonAccountCredentials {
  private final String accountId
  private final String role

  AmazonRoleAccountCredentials(AWSCredentialsProvider provider, String simpleDBDomain, String accountId, String name, String role) {
    super(provider, name, simpleDBDomain)
    this.accountId = accountId
    this.role = role
  }

  @JsonIgnore
  @XmlTransient
  @Transient
  public AmazonCredentials getCredentials() {
    AWSCredentials credentials = new STSAssumeRoleSessionCredentialsProvider(provider, "arn:aws:iam::${accountId}:role/${role}", "Spinnaker")?.credentials
    new AmazonCredentials(credentials, name, null)
  }

  @JsonIgnore
  @XmlTransient
  @Transient
  public String getAccountId() {
    accountId
  }

}

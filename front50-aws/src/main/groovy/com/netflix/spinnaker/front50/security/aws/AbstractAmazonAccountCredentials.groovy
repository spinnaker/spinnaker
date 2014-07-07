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

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.simpledb.AmazonSimpleDBClient
import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.amazoncomponents.security.AmazonCredentials
import com.netflix.spinnaker.front50.model.application.AmazonApplicationDAO
import com.netflix.spinnaker.front50.model.application.Application
import com.netflix.spinnaker.front50.security.NamedAccount

import javax.xml.bind.annotation.XmlTransient

abstract class AbstractAmazonAccountCredentials implements NamedAccount<AmazonCredentials> {
  final String name
  @JsonIgnore
  @XmlTransient
  final AWSCredentialsProvider provider
  @JsonIgnore
  @XmlTransient
  final Class<AmazonCredentials> type = AmazonCredentials

  final String simpleDBDomain

  AbstractAmazonAccountCredentials(AWSCredentialsProvider provider, String name, String simpleDBDomain) {
    this.provider = provider
    this.name = name
    this.simpleDBDomain = simpleDBDomain
  }

  @JsonIgnore
  @XmlTransient
  @Override
  Application getApplication() {
    def dao = new AmazonApplicationDAO(awsSimpleDBClient: new AmazonSimpleDBClient(provider), domain: simpleDBDomain)
    new Application(dao: dao)
  }
}

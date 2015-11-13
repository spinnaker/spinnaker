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

package com.netflix.spinnaker.kato.aws.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.aws.deploy.description.UpsertAmazonDNSDescription
import com.netflix.spinnaker.kato.aws.deploy.ops.dns.UpsertAmazonDNSAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

class UpsertAmazonDNSAtomicOperationConverterUnitSpec extends Specification {
  @Shared
  UpsertAmazonDNSAtomicOperationConverter converter

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  def setupSpec() {
    this.converter = new UpsertAmazonDNSAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(NetflixAmazonCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "basicAmazonDeployDescription type returns BasicAmazonDeployDescription and DeployAtomicOperation"() {
    setup:
    def input = [type: "CNAME", name: "kato.test.netflix.net.", hostedZoneName: "test.netflix.net.", credentials: "test"]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof UpsertAmazonDNSDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof UpsertAmazonDNSAtomicOperation
  }
}

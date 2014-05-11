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

package com.netflix.asgard.kato.deploy.aws.health

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeAccountAttributesResult
import com.netflix.asgard.kato.deploy.aws.StaticAmazonClients
import com.netflix.asgard.kato.health.AmazonHealthIndicator
import com.netflix.asgard.kato.security.NamedAccountCredentials
import com.netflix.asgard.kato.security.NamedAccountCredentialsHolder
import com.netflix.asgard.kato.security.aws.AmazonCredentials
import com.netflix.asgard.kato.security.aws.AmazonNamedAccountCredentials
import org.springframework.boot.actuate.endpoint.HealthEndpoint
import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter
import org.springframework.http.HttpStatus
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class AmazonHealthIndicatorSpec extends Specification {

  def "health fails when no aws credentials are available"() {
    setup:
    def holder = Mock(NamedAccountCredentialsHolder)
    holder.getAccountNames() >> ["foo"]
    holder.getCredentials("foo") >> Mock(NamedAccountCredentials)
    def endpoint = new EndpointMvcAdapter(new HealthEndpoint(new AmazonHealthIndicator(namedAccountCredentialsHolder: holder)))
    def mvc = standaloneSetup(endpoint).setMessageConverters new MappingJackson2HttpMessageConverter() build()

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/health")).andReturn()

    then:
    result.response.status == HttpStatus.INTERNAL_SERVER_ERROR.value()
  }

  def "health fails when amazon appears unreachable"() {
    setup:
    def holder = Mock(NamedAccountCredentialsHolder)
    holder.getAccountNames() >> ["foo"]
    def creds = Mock(AmazonNamedAccountCredentials)
    creds.getCredentials() >> Mock(AWSCredentials)
    holder.getCredentials("foo") >> creds
    def mockEc2 = Mock(AmazonEC2)
    mockEc2.describeAccountAttributes() >> { throw new AmazonServiceException("fail") }
    StaticAmazonClients.metaClass.'static'.getAmazonEC2 = { AmazonCredentials amazonCredentials, String region -> mockEc2 }
    def endpoint = new EndpointMvcAdapter(new HealthEndpoint(new AmazonHealthIndicator(namedAccountCredentialsHolder: holder)))
    def mvc = standaloneSetup(endpoint).setMessageConverters new MappingJackson2HttpMessageConverter() build()

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/health")).andReturn()

    then:
    result.response.status == HttpStatus.SERVICE_UNAVAILABLE.value()
  }

  def "health succeeds when amazon is reachable"() {
    setup:
    def holder = Mock(NamedAccountCredentialsHolder)
    holder.getAccountNames() >> ["foo"]
    def creds = Mock(AmazonNamedAccountCredentials)
    creds.getCredentials() >> Mock(AWSCredentials)
    holder.getCredentials("foo") >> creds
    def mockEc2 = Mock(AmazonEC2)
    mockEc2.describeAccountAttributes() >> { Mock(DescribeAccountAttributesResult) }
    StaticAmazonClients.metaClass.'static'.getAmazonEC2 = { AmazonCredentials amazonCredentials, String region -> mockEc2 }
    def endpoint = new EndpointMvcAdapter(new HealthEndpoint(new AmazonHealthIndicator(namedAccountCredentialsHolder: holder)))
    def mvc = standaloneSetup(endpoint).setMessageConverters new MappingJackson2HttpMessageConverter() build()

    when:
    def result = mvc.perform(MockMvcRequestBuilders.get("/health")).andReturn()

    then:
    result.response.status == HttpStatus.OK.value()
  }
}

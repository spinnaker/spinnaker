/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.config
import com.netflix.spinnaker.clouddriver.cf.deploy.handlers.CloudFoundryDeployHandler
import com.netflix.spinnaker.clouddriver.cf.utils.CloudFoundryClientFactory
import com.netflix.spinnaker.clouddriver.cf.utils.RestTemplateFactory
import com.netflix.spinnaker.clouddriver.cf.utils.S3ServiceFactory
import com.netflix.spinnaker.clouddriver.core.CloudDriverConfig
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.test.IntegrationTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.web.context.support.GenericWebApplicationContext
import spock.lang.Specification

@WebAppConfiguration
@ContextConfiguration(classes = [TestConfiguration])
@IntegrationTest(['cf.enabled:true', 'services.front50.enabled:false',
    'cf.accounts[0].name:dev', 'cf.accounts[0].username:me@example.com', 'cf.accounts[0].password:my-password'])
class CloudFoundryConfigSpec extends Specification {

  @Autowired
  GenericWebApplicationContext context

  void "verify basic configuration"() {
    when:
    def properties = context.getBean(CloudFoundryConfigurationProperties)
    def clientFactory = context.getBean(CloudFoundryClientFactory)
    def deployHandler = context.getBean(CloudFoundryDeployHandler)
    def operationPoller = context.getBean(OperationPoller)

    then:
    properties.accounts.size() == 1
    properties.accounts[0].name == 'dev'
    properties.accounts[0].username == 'me@example.com'
    properties.accounts[0].password == 'my-password'

    clientFactory != null

    deployHandler.clientFactory == clientFactory
    deployHandler.restTemplateFactory instanceof RestTemplateFactory
    deployHandler.s3ServiceFactory instanceof S3ServiceFactory

    operationPoller != null
  }

  @Configuration
  @ComponentScan(["com.netflix.spinnaker.clouddriver.cf"])
  @Import([CloudDriverConfig])
  @EnableAutoConfiguration(exclude = [BatchAutoConfiguration, GroovyTemplateAutoConfiguration])
  static class TestConfiguration {

  }

}

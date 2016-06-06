/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.converters.servergroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.openstack.deploy.converters.DeployOpenstackAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.DeployOpenstackAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup.DeployOpenstackAtomicOperation
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class DeployOpenstackAtomicOperationConverterSpec extends Specification {
  private static final String ACCOUNT_NAME = 'myaccount'
  private static final APPLICATION = "app"
  private static final STACK = "stack"
  private static final REGION = "region"
  private static final DETAILS = "details"
  private static final String HEAT_TEMPLATE = '{"heat_template_version":"2013-05-23",' +
                                              '"description":"Simple template to test heat commands",' +
                                              '"parameters":{"flavor":{' +
                                              '"default":"1 GB General Purpose v1","type":"string"}},' +
                                              '"resources":{"hello_world":{"type":"OS::Nova::Server",' +
                                              '"properties":{"key_name":"heat_key","flavor":{"get_param":"flavor"},' +
                                              '"image":"Ubuntu 12.04 LTS (Precise Pangolin) (PV)",' +
                                              '"user_data":"#!/bin/bash -xv\\necho \\"hello world\\" &gt; /root/hello-world.txt\\n"}}}}'
  private static final Integer TIMEOUT_MINS = 5
  private static final Map<String,String> PARAMS_MAP = Collections.emptyMap()
  private static final Boolean DISABLE_ROLLBACK = false

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DeployOpenstackAtomicOperationConverter converter

  def mockCredentials

  def setupSpec() {
    converter = new DeployOpenstackAtomicOperationConverter(objectMapper: mapper)
  }

  def setup() {
    mockCredentials = Mock(OpenstackNamedAccountCredentials)
    converter.accountCredentialsProvider = Mock(AccountCredentialsProvider)
  }

  void "DeployOpenstackAtomicOperationConverter type returns DeployOpenstackAtomicOperation and DeployOpenstackAtomicOperationDescription"() {
    setup:
    def input = [stack: STACK,
                 application: APPLICATION,
                 freeFormDetails: DETAILS,
                 region: REGION,
                 heatTemplate: HEAT_TEMPLATE,
                 parameters: PARAMS_MAP,
                 disableRollback: DISABLE_ROLLBACK,
                 timeoutMins: TIMEOUT_MINS,
                 account: ACCOUNT_NAME]
    when:
    def description = converter.convertDescription(input)

    then:
    1 * converter.accountCredentialsProvider.getCredentials(_) >> mockCredentials
    description instanceof DeployOpenstackAtomicOperationDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    1 * converter.accountCredentialsProvider.getCredentials(_) >> mockCredentials
    operation instanceof DeployOpenstackAtomicOperation
  }
}

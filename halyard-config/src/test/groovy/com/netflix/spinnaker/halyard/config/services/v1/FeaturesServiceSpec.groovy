/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.services.v1

import spock.lang.Specification

class FeaturesServiceSpec extends Specification {
  String DEPLOYMENT = "default"
  HalconfigParserMocker mocker = new HalconfigParserMocker()

  FeaturesService makeFeaturesService(String config) {
    def lookupService = new LookupService()
    def deploymentService = new DeploymentService()
    def featuresService = new FeaturesService()

    lookupService.parser = mocker.mockHalconfigParser(config)
    deploymentService.lookupService = lookupService

    featuresService.lookupService = lookupService
    featuresService.deploymentService = deploymentService
    return featuresService
  }

  def "load an existent feature node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  features:
    chaos: true
"""
    def featuresService = makeFeaturesService(config)

    when:
    def result = featuresService.getFeatures(DEPLOYMENT)

    then:
    result != null
    result.chaos
  }

  def "load a non-existent feature node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
"""
    def featuresService = makeFeaturesService(config)

    when:
    def result = featuresService.getFeatures(DEPLOYMENT)

    then:
    result != null
  }

  def "load a non-default deployment's feature node"() {
    setup:
    def deployment2 = "non-default"
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $deployment2
  version: 1
  providers: null
"""
    def featuresService = makeFeaturesService(config)

    when:
    def result = featuresService.getFeatures(deployment2)

    then:
    result != null
  }
}

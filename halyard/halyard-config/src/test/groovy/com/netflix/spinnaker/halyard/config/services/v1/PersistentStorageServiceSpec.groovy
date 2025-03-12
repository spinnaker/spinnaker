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

import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.GcsPersistentStore
import spock.lang.Specification

class PersistentStorageServiceSpec extends Specification {
  String DEPLOYMENT = "default"
  String PROJECT = "my-project"
  String STORE_NAME = "gcs"
  HalconfigParserMocker mocker = new HalconfigParserMocker()

  PersistentStorageService makePersistentStorageService(String config) {
    def lookupService = new LookupService()
    def deploymentService = new DeploymentService()
    def persistentStorageService = new PersistentStorageService()

    lookupService.parser = mocker.mockHalconfigParser(config)
    deploymentService.lookupService = lookupService

    persistentStorageService.lookupService = lookupService
    persistentStorageService.deploymentService = deploymentService
    return persistentStorageService
  }

  def "load an existent persistentStorage node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  persistentStorage:
    persistentStoreType: $STORE_NAME
"""
    def persistentStorageService = makePersistentStorageService(config)

    when:
    def result = persistentStorageService.getPersistentStorage(DEPLOYMENT)

    then:
    result != null
    result.getPersistentStoreType().getId() == STORE_NAME
  }

  def "load a non-existent persistentStorage node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
"""
    def persistentStorageService = makePersistentStorageService(config)

    when:
    def result = persistentStorageService.getPersistentStorage(DEPLOYMENT)

    then:
    result != null
  }

  def "load a non-default deployment's persistentStorage node"() {
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
    def persistentStorageService = makePersistentStorageService(config)

    when:
    def result = persistentStorageService.getPersistentStorage(deployment2)

    then:
    result != null
  }

  def "load an existent persistentStore node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
  persistentStorage:
    $STORE_NAME:
      project: $PROJECT
"""
    def persistentStorageService = makePersistentStorageService(config)

    when:
    def result = (GcsPersistentStore) persistentStorageService.getPersistentStore(DEPLOYMENT, STORE_NAME)

    then:
    result != null
    result.project == PROJECT
  }

  def "load a non-existent persistentStore node"() {
    setup:
    String config = """
halyardVersion: 1
currentDeployment: $DEPLOYMENT
deploymentConfigurations:
- name: $DEPLOYMENT
  version: 1
  providers: null
"""
    def persistentStorageService = makePersistentStorageService(config)

    when:
    def result = persistentStorageService.getPersistentStore(DEPLOYMENT, STORE_NAME)

    then:
    result != null
  }
}

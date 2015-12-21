/*
 * Copyright 2015 Netflix, Inc.
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


package com.netflix.spinnaker.kato.aws.deploy.userdata

import spock.lang.Specification

class LocalFileUserDataProviderSpec extends Specification {

  final String APP = 'app'
  final String STACK = 'stack'
  final String COUNTRIES = 'countries'
  final String DEV_PHASE = 'devPhase'
  final String HARDWARE = 'hardware'
  final String PARTNERS = 'partners'
  final String REVISION = 99
  final String ZONE = 'zone'
  final String REGION = 'region'
  final String ACCOUNT = 'account'
  final String ENVIRONMENT = 'environment'
  final String ACCOUNT_TYPE = 'accountType'

  final String ASG_NAME = "${APP}-${STACK}-c0${COUNTRIES}-d0${DEV_PHASE}-h0${HARDWARE}-p0${PARTNERS}-r0${REVISION}-z0${ZONE}"
  final String LAUNCH_CONFIG_NAME = 'launchConfigName'

  void "replaces expected strings"() {
    given:
    LocalFileUserDataProvider localFileUserDataProvider = GroovySpy()
    localFileUserDataProvider.localFileUserDataProperties = new LocalFileUserDataProperties()
    localFileUserDataProvider.assembleUserData(_, _, _) >> getRawUserData()

    when:
    def userData = localFileUserDataProvider.getUserData(ASG_NAME, LAUNCH_CONFIG_NAME, REGION, ACCOUNT, ENVIRONMENT, ACCOUNT_TYPE)

    then:
    userData == getFormattedUserData()
  }

  String getRawUserData() {
    return [
      "export ACCOUNT=%%account%%",
      "export ACCOUNT_TYPE=%%accounttype%%",
      "export ENV=%%env%%",
      "export APP=%%app%%",
      "export REGION=%%region%%",
      "export GROUP=%%group%%",
      "export AUTOGRP=%%autogrp%%",
      "export REVISION=%%revision%%",
      "export COUNTRIES=%%countries%%",
      "export DEV_PHASE=%%devPhase%%",
      "export HARDWARE=%%hardware%%",
      "export ZONE=%%zone%%",
      "export CLUSTER=%%cluster%%",
      "export STACK=%%stack%%",
      "export LAUNCH_CONFIG=%%launchconfig%%",
    ].join('\n')
  }

  String getFormattedUserData() {
    return [
      "export ACCOUNT=${ACCOUNT}",
      "export ACCOUNT_TYPE=${ACCOUNT_TYPE}",
      "export ENV=${ACCOUNT}",
      "export APP=${APP}",
      "export REGION=${REGION}",
      "export GROUP=${ASG_NAME}",
      "export AUTOGRP=${ASG_NAME}",
      "export REVISION=${REVISION}",
      "export COUNTRIES=${COUNTRIES}",
      "export DEV_PHASE=${DEV_PHASE}",
      "export HARDWARE=${HARDWARE}",
      "export ZONE=${ZONE}",
      "export CLUSTER=${ASG_NAME}",
      "export STACK=${STACK}",
      "export LAUNCH_CONFIG=${LAUNCH_CONFIG_NAME}",
    ].join('\n')
  }

}

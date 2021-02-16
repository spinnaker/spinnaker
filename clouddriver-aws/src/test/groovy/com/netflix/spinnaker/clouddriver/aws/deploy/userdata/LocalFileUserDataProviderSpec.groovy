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


package com.netflix.spinnaker.clouddriver.aws.deploy.userdata

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.LaunchConfigurationBuilder.LaunchConfigurationSettings
import com.netflix.spinnaker.clouddriver.aws.userdata.UserDataInput
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import spock.lang.Specification

class LocalFileUserDataProviderSpec extends Specification {

  static final String APP = 'app'
  static final String STACK = 'stack'
  static final String COUNTRIES = 'countries'
  static final String DEV_PHASE = 'devPhase'
  static final String HARDWARE = 'hardware'
  static final String PARTNERS = 'partners'
  static final String REVISION = 99
  static final String ZONE = 'zone'
  static final String REGION = 'region'
  static final String ACCOUNT = 'account'
  static final String ENVIRONMENT = 'environment'
  static final String ACCOUNT_TYPE = 'accountType'
  static final String DETAIL = "detail-c0${COUNTRIES}-d0${DEV_PHASE}-h0${HARDWARE}-p0${PARTNERS}-r0${REVISION}-z0${ZONE}"

  static final String ASG_NAME = "${APP}-${STACK}-${DETAIL}"
  static final String LAUNCH_CONFIG_NAME = 'launchConfigName'

  static final LaunchConfigurationSettings SETTINGS = LaunchConfigurationSettings.builder()
      .baseName(ASG_NAME)
      .region(REGION)
      .account(ACCOUNT)
      .environment(ENVIRONMENT)
      .accountType(ACCOUNT_TYPE)
      .build()

  static final UserDataInput INPUT = UserDataInput
    .builder()
    .asgName(SETTINGS.baseName)
    .launchSettingName(LAUNCH_CONFIG_NAME)
    .environment(SETTINGS.environment)
    .region(SETTINGS.region)
    .account(SETTINGS.account)
    .accountType(SETTINGS.accountType)
    .build()

  void "replaces expected strings"() {
    given:
    LocalFileUserDataProvider localFileUserDataProvider = GroovySpy()
    localFileUserDataProvider.localFileUserDataProperties = new LocalFileUserDataProperties()
    localFileUserDataProvider.defaultUserDataTokenizer = new DefaultUserDataTokenizer()
    localFileUserDataProvider.isLegacyUdf(_, _) >> legacyUdf
    localFileUserDataProvider.assembleUserData(legacyUdf, _, _, _) >> getRawUserData()

    when:
    def userData = localFileUserDataProvider.getUserData(INPUT)

    then:
    userData == getFormattedUserData(expectedEnvironment)

    where:
    legacyUdf | expectedEnvironment
    true      | ACCOUNT
    false     | ENVIRONMENT
  }

  static String getRawUserData() {
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
      "export DETAIL=%%detail%%",
      "export LAUNCH_CONFIG=%%launchconfig%%",
    ].join('\n')
  }

  static String getFormattedUserData(String expectedEnvironment) {
    return [
      "export ACCOUNT=${ACCOUNT}",
      "export ACCOUNT_TYPE=${ACCOUNT_TYPE}",
      "export ENV=${expectedEnvironment}",
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
      "export DETAIL=${DETAIL}",
      "export LAUNCH_CONFIG=${LAUNCH_CONFIG_NAME}",
    ].join('\n')
  }
}

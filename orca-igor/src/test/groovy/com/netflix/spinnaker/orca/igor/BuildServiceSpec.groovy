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

package com.netflix.spinnaker.orca.igor

import spock.lang.Specification

class BuildServiceSpec extends Specification {

  BuildService buildService
  IgorService igorService

  private static final MASTER = 'MASTER'
  private static final BUILD_NUMBER = 123
  private static final JOB_NAME = "name/with/slashes and spaces"
  private static final JOB_NAME_ENCODED = "name/with/slashes%20and%20spaces"
  private static final PARAMS = ['key': 'value']
  private static final FILENAME = 'file.txt'
  private static final QUEUED_BUILD = '12'

  void setup() {
    igorService = Mock(IgorService)
    buildService = new BuildService(igorService, new IgorFeatureFlagProperties())
  }

  void 'build method encodes the job name'() {
    when:
    buildService.build(MASTER, JOB_NAME, PARAMS)

    then:
    1 * igorService.build(MASTER, JOB_NAME_ENCODED, PARAMS, '')
  }

  void 'getBuild method encodes the job name'() {
    when:
    buildService.getBuild(BUILD_NUMBER, MASTER, JOB_NAME)

    then:
    1 * igorService.getBuild(BUILD_NUMBER, MASTER, JOB_NAME_ENCODED)
  }

  void 'getPropertyFile method encodes the job name'() {
    when:
    buildService.getPropertyFile(BUILD_NUMBER, FILENAME, MASTER, JOB_NAME)

    then:
    1 * igorService.getPropertyFile(BUILD_NUMBER, FILENAME, MASTER, JOB_NAME_ENCODED)
  }

  void 'stop method sends job name in path when flag is false'() {
    when:
    buildService.stop(MASTER, JOB_NAME, QUEUED_BUILD, BUILD_NUMBER )

    then:
    1 * igorService.stop(MASTER, JOB_NAME, QUEUED_BUILD, BUILD_NUMBER, '')
  }

  void 'stop method sends job name in query when flag is true'() {
    IgorFeatureFlagProperties igorFeatureFlagProperties = new IgorFeatureFlagProperties()
    igorFeatureFlagProperties.setJobNameAsQueryParameter(true)
    buildService = new BuildService(igorService, igorFeatureFlagProperties)

    when:
    buildService.stop(MASTER, JOB_NAME, QUEUED_BUILD, BUILD_NUMBER )

    then:
    1 * igorService.stopWithJobNameAsQueryParameter(MASTER, JOB_NAME, QUEUED_BUILD, BUILD_NUMBER, '')
  }
}

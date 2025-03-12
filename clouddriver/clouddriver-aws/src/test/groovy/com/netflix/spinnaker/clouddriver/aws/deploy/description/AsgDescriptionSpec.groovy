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


package com.netflix.spinnaker.clouddriver.aws.deploy.description

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AsgDescription
import spock.lang.Specification

class AsgDescriptionSpec extends Specification {
  def "should favor `serverGroupName` over `asgName`"() {
    expect:
    aD("serverGroupName", "asgName").asgName == "serverGroupName"
    aD("serverGroupName", "asgName").serverGroupName == "serverGroupName"

    aD(null, "asgName").asgName == "asgName"
    aD(null, "asgName").serverGroupName == "asgName"

    aD("serverGroupName", "asgName").@serverGroupName == "serverGroupName"
    aD("serverGroupName", "asgName").@asgName == "asgName"
  }

  private static AsgDescription aD(String serverGroupName, String asgName) {
    return new AsgDescription(serverGroupName: serverGroupName, asgName: asgName)
  }
}

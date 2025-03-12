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

package com.netflix.spinnaker.orca.kato.pipeline.support

import spock.lang.Specification

class AsgDescriptionSupportSpec extends Specification {

  void "should group asgs by region"() {
    setup:
    def asgs = [
        [ asgName: "asg-v001", region: "us-east-1"],
        [ asgName: "asg-v002", region: "us-east-1"],
        [ asgName: "asg-v003", region: "us-west-1"],
    ]

    expect:
    AsgDescriptionSupport.convertAsgsToDeploymentTargets(asgs).toString() == ['us-east-1': ['asg-v001', 'asg-v002'], 'us-west-1': ['asg-v003']].toString()
  }
}

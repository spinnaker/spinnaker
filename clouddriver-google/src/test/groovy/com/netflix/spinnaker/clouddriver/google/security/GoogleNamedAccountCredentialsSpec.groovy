/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.security

import com.google.api.services.compute.model.Region
import com.google.api.services.compute.model.RegionList
import spock.lang.Specification

class GoogleNamedAccountCredentialsSpec extends Specification {

  def 'regionlist should convert to map'() {
    setup:
      Region r1 = new Region()
      r1.setName("region1")
      r1.setZones(['region1/z1', 'region1/z2', 'region1/z3'])

      Region r2 = new Region()
      r2.setName("region2")
      r2.setZones(['region2/z4', 'region2/z5', 'region2/z6'])

      RegionList rl = new RegionList()
      rl.setItems([r1, r2])

    when:
      def map = GoogleNamedAccountCredentials.convertToMap(rl)

    then:
      map.size() == 2
      map.region1 == ['z1', 'z2', 'z3']
      map.region2 == ['z4', 'z5', 'z6']
  }
}

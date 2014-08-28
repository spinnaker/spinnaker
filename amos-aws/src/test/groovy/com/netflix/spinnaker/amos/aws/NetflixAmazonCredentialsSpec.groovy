/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.amos.aws

import com.netflix.spinnaker.amos.YamlAccountCredentialsFactory
import spock.lang.Shared
import spock.lang.Specification

class NetflixAmazonCredentialsSpec extends Specification {
  @Shared
  def factory = new YamlAccountCredentialsFactory()

  void "should load netflix account credentials from yaml"() {
    given:
      def creds = factory.load(yaml, NetflixAmazonCredentials)

    expect:
      creds instanceof NetflixAmazonCredentials

    and:
      creds.name == "test"
      creds.accountId == 1234567
      creds.edda == "http://edda.%s.test.netflix.net"
      creds.discovery == "http://%s.discoverytest.netflix.net/v1/discovery"
      creds.front50 == "http://front50.test.netflix.net"
      creds.regions.size() == 1
      creds.regions[0].name == "us-east-1"
      creds.regions[0].availabilityZones.size() == 1
      creds.regions[0].availabilityZones[0] == "us-east-1a"

    where:
      yaml = """\
        |name: test
        |accountId: 1234567
        |edda: http://edda.%s.test.netflix.net
        |discovery: http://%s.discoverytest.netflix.net/v1/discovery
        |front50: http://front50.test.netflix.net
        |regions:
        | - name: us-east-1
        |   availabilityZones:
        |     - us-east-1a
      """.stripMargin()
  }
}

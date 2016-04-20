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

package com.netflix.spinnaker.clouddriver.google.model.callbacks


import spock.lang.Specification

class UtilsSpec extends Specification {

  private final static String ACCOUNT_NAME = "test-account"
  private final static String APPLICATION_NAME = "testapp"
  private final static String CLUSTER_DEV_NAME = "testapp-dev"
  private final static String CLUSTER_PROD_NAME = "testapp-prod"
  private final static String SERVER_GROUP_NAME = "testapp-dev-v000"
  private final static String INSTANCE_NAME = "testapp-dev-v000-abcd"
  private final static String LOAD_BALANCER_NAME = "testapp-dev-frontend"
  private final static String REGION = "us-central1"

  void "getImmutableCopy returns an immutable copy"() {
    when:
      def origList = ["abc", "def", "ghi"]
      def copyList = Utils.getImmutableCopy(origList)

    then:
      origList == copyList

    when:
      origList += "jkl"

    then:
      origList != copyList

    when:
      def origMap = [abc: 123, def: 456, ghi: 789]
      def copyMap = Utils.getImmutableCopy(origMap)

    then:
      origMap == copyMap

    when:
      origMap["def"] = 654

    then:
      origMap != copyMap

      Utils.getImmutableCopy(5) == 5
      Utils.getImmutableCopy("some-string") == "some-string"
  }

  def "should get zone from instance URL"() {
    expect:
      expected == Utils.getZoneFromInstanceUrl(input)

    where:
      input                                                                                                                        || expected
      "https://content.googleapis.com/compute/v1/projects/ttomsu-dev-spinnaker/zones/us-central1-c/instances/sekret-gce-v070-z8mh" || "us-central1-c"
      "projects/ttomsu-dev-spinnaker/zones/us-central1-c/instances/sekret-gce-v070-z8mh"                                           || "us-central1-c"
  }
}

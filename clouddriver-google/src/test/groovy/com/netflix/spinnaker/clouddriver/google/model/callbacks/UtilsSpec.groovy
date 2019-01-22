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

import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleTargetProxyType
import spock.lang.Specification
import spock.lang.Unroll

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

  def "should get target type from target proxy URL"() {
    expect:
      expected == Utils.getTargetProxyType(input)

    where:
      input                                                                                                  | expected
      "https://compute.googleapis.com/compute/v1/projects/spinnaker-jtk54/global/targetHttpsProxies/https-proxy" | GoogleTargetProxyType.HTTPS
      "https://compute.googleapis.com/compute/v1/projects/spinnaker-jtk54/global/targetHttpProxies/http-proxy"   | GoogleTargetProxyType.HTTP
      "https://compute.googleapis.com/compute/v1/projects/spinnaker-jtk54/global/targetSslProxies/ssl-proxy"     | GoogleTargetProxyType.SSL
      "https://compute.googleapis.com/compute/v1/projects/spinnaker-jtk54/global/targetTcpProxies/tcp-proxy"     | GoogleTargetProxyType.TCP
      "projects/spinnaker-jtk54/global/targetHttpsProxies/https-proxy"                                       | GoogleTargetProxyType.HTTPS
      "projects/spinnaker-jtk54/global/targetHttpProxies/http-proxy"                                         | GoogleTargetProxyType.HTTP
      "projects/spinnaker-jtk54/global/targetSslProxies/ssl-proxy"                                           | GoogleTargetProxyType.SSL
      "projects/spinnaker-jtk54/global/targetTcpProxies/tcp-proxy"                                           | GoogleTargetProxyType.TCP
  }

  def "should get region from a full group Url"() {
    expect:
      expected == Utils.getRegionFromGroupUrl(input)

    where:
      input                                                                                                      | expected
      "https://compute.googleapis.com/compute/v1/projects/PROJECT/zones/us-central1-f/instanceGroups/svg-stack-v000" | "us-central1"
      "/projects/PROJECT/zones/us-central1-f/instanceGroups/svg-stack-v000"                                      | "us-central1"
      "https://compute.googleapis.com/compute/v1/projects/PROJECT/regions/us-central1/instanceGroups/svg-stack-v00"  | "us-central1"
      "projects/PROJECT/regions/us-central1/instanceGroups/svg-stack-v00"                                        | "us-central1"
  }

  @Unroll
  def "should represent port range as a single port string if ports equal"() {
    expect:
      expected == Utils.derivePortOrPortRange(input)

    where:
      input       | expected
      ""          | ""
      "80-90"     | "80-90"
      "8080-8080" | "8080"
  }

  def "should return health check type from full url"() {
    expect:
      expected == Utils.getHealthCheckType(input)

    where:
      input                                                                                                | expected
      "https://compute.googleapis.com/compute/beta/projects/spinnaker-jtk54/global/healthChecks/jake-ilb"      | "healthChecks"
      "https://compute.googleapis.com/compute/beta/projects/spinnaker-jtk54/global/httpHealthChecks/jake-ilb"  | "httpHealthChecks"
      "https://compute.googleapis.com/compute/beta/projects/spinnaker-jtk54/global/httpsHealthChecks/jake-ilb" | "httpsHealthChecks"
  }

  @Unroll
  void "should decorate xpn resource id"() {
    expect:
      Utils.decorateXpnResourceIdIfNeeded(managedProjectId, xpnResource) == decoratedXpnResourceId

    where:
      managedProjectId | xpnResource                                                            || decoratedXpnResourceId
      "my-svc-project" | "projects/my-host-project/global/networks/some-network"                || "my-host-project/some-network"
      "my-svc-project" | "projects/my-host-project/regions/us-central1/subnetworks/some-subnet" || "my-host-project/some-subnet"
      "my-svc-project" | "projects/my-svc-project/global/networks/some-network"                 || "some-network"
      "my-svc-project" | "projects/my-svc-project/regions/us-central1/subnetworks/some-subnet"  || "some-subnet"
  }
}

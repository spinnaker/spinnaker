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

package com.netflix.spinnaker.oort

import com.netflix.spinnaker.oort.controllers.ApplicationsController
import com.netflix.spinnaker.oort.model.ApplicationProvider
import com.netflix.spinnaker.oort.model.aws.AmazonApplication
import spock.lang.Shared
import spock.lang.Specification

import java.lang.Void as Should

class ApplicationControllerSpec extends Specification {

  @Shared
  ApplicationsController applicationsController

  def setup() {
    applicationsController = new ApplicationsController()
  }

  Should "call all application providers on listing"() {
    setup:
      def appProvider1 = Mock(ApplicationProvider)
      def appProvider2 = Mock(ApplicationProvider)
      applicationsController.applicationProviders = [appProvider1, appProvider2]

    when:
      applicationsController.list()

    then:
      1 * appProvider1.getApplications()
      1 * appProvider2.getApplications()
  }

  Should "merge clusterNames and attributes when multiple apps are found"() {
    setup:
      def appProvider1 = Mock(ApplicationProvider)
      def appProvider2 = Mock(ApplicationProvider)
      applicationsController.applicationProviders = [appProvider1, appProvider2]
      def app1 = new AmazonApplication(name: "foo", clusterNames: [test: ["bar"] as Set], attributes: [tag: "val"])
      def app2 = new AmazonApplication(name: "foo", clusterNames: [test: ["baz"] as Set], attributes: [:])

    when:
      def result = applicationsController.get("foo")

    then:
      appProvider1.getApplication("foo") >> app1
      appProvider2.getApplication("foo") >> app2
      result.name == "foo"
      result.clusterNames == [test: ["bar", "baz"] as Set]
      result.attributes == [tag: "val"]
  }
}

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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll

class StageDataSpec extends Specification {

  @Unroll
  void "should include freeFormDetails when building cluster name"() {
    given:
      def stage = new Stage<>(
        new Pipeline("orca"),
          "whatever",
          [
              application    : application,
              stack          : stack,
              freeFormDetails: freeFormDetails
          ]
      )

    expect:
      stage.mapTo(StageData).getCluster() == cluster

    where:
      application | stack        | freeFormDetails || cluster
      "myapp"     | "prestaging" | "freeform"      || "myapp-prestaging-freeform"
      "myapp"     | "prestaging" | null            || "myapp-prestaging"
      "myapp"     | null         | "freeform"      || "myapp--freeform"
      "myapp"     | null         | null            || "myapp"
  }

  @Unroll
  void "stage data should favor account over credentials"() {
    given:
      def stage = new Stage<>(
        new Pipeline("orca"),
          "whatever",
          [
              account    : account,
              credentials: credentials
          ]
      )

    when:
      def mappedAccount
      try {
        mappedAccount = stage.mapTo(StageData).getAccount()
      } catch (Exception e) {
        mappedAccount = e.class.simpleName
      }

    then:
      mappedAccount == expectedAccount

    where:
      account | credentials || expectedAccount
      "test"  | "prod"      || "IllegalStateException"
      "test"  | null        || "test"
      null    | "test"      || "test"
  }

  @Unroll
  void "should check both useSourceCapacity and source.useSourceCapacity"() {
    expect:
    new StageData(useSourceCapacity: useSourceCapacity, source: source).getUseSourceCapacity() == expectedUseSourceCapacity

    where:
    useSourceCapacity | source                                         || expectedUseSourceCapacity
    true              | null                                           || true
    true              | new StageData.Source(useSourceCapacity: true)  || true
    true              | new StageData.Source(useSourceCapacity: false) || false
    false             | new StageData.Source(useSourceCapacity: true)  || true
    false             | null                                           || false
    null              | new StageData.Source()                         || false
    null              | null                                           || false
  }
}

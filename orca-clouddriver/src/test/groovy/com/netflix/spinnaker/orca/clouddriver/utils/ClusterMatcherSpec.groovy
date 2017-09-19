/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.utils

import spock.lang.Specification
import spock.lang.Unroll

class ClusterMatcherSpec extends Specification {

  String account = "test"
  String location = "us-east-1"
  String stack = "stack"
  String detail = "detail"
  String clusterName = "myapp-stack-detail"

  void "returns null when no rules provided"() {
    expect:
    ClusterMatcher.getMatchingRule(account, location, clusterName, null) == null
  }

  void "returns null when no rules match on account, location, or stack/detail"() {
    given:
    List<ClusterMatchRule> rules = [
      new ClusterMatchRule(account: account, location: location, stack: "*", detail: "*", priority: 1)
    ]

    expect:
    ClusterMatcher.getMatchingRule("prod", location, clusterName, rules) == null
  }

  void "returns rule based on location if accounts are identical"() {
    given:
    ClusterMatchRule expected = new ClusterMatchRule(account: account, location: location, stack: "*", detail: "*", priority: 1)
    List<ClusterMatchRule> rules = [
      new ClusterMatchRule(account: account, location: "*", stack: "*", detail: "*", priority: 1),
      expected
    ]

    expect:
    ClusterMatcher.getMatchingRule(account, location, clusterName, rules) == expected
  }

  void "returns rule based on stack if account and location are identical"() {
    given:
    ClusterMatchRule expected = new ClusterMatchRule(account: account, location: location, stack: stack, detail: "*", priority: 1)
    List<ClusterMatchRule> rules = [
      new ClusterMatchRule(account: account, location: location, stack: "*", detail: "*", priority: 1),
      expected
    ]

    expect:
    ClusterMatcher.getMatchingRule(account, location, clusterName, rules) == expected
  }

  void "returns rule based on detail if account, location, and stack are identical"() {
    given:
    ClusterMatchRule expected = new ClusterMatchRule(account: account, location: location, stack: stack, detail: detail, priority: 1)
    List<ClusterMatchRule> rules = [
      new ClusterMatchRule(account: account, location: location, stack: "*", detail: "*", priority: 1),
      expected
    ]

    expect:
    ClusterMatcher.getMatchingRule(account, location, clusterName, rules) == expected
  }

  void "returns rule based on priority if all other fields match"() {
    given:
    ClusterMatchRule expected = new ClusterMatchRule(account: account, location: location, stack: stack, detail: detail, priority: 1)
    List<ClusterMatchRule> rules = [
      new ClusterMatchRule(account: account, location: location, stack: stack, detail: detail, priority: 2),
      expected
    ]

    expect:
    ClusterMatcher.getMatchingRule(account, location, clusterName, rules) == expected
  }

  void "specific account takes priority over all other wildcard fields"() {
    given:
    ClusterMatchRule expected = new ClusterMatchRule(account: account, location: "*", stack: "*", detail: "*", priority: 2)
    List<ClusterMatchRule> rules = [
      new ClusterMatchRule(account: "*", location: location, stack: stack, detail: detail, priority: 1),
      expected
    ]

    expect:
    ClusterMatcher.getMatchingRule(account, location, clusterName, rules) == expected
  }

  void "specific location takes priority over wildcard stack, detail"() {
    given:
    ClusterMatchRule expected = new ClusterMatchRule(account: account, location: location, stack: "*", detail: "*", priority: 2)
    List<ClusterMatchRule> rules = [
      new ClusterMatchRule(account: account, location: "*", stack: stack, detail: detail, priority: 1),
      expected
    ]

    expect:
    ClusterMatcher.getMatchingRule(account, location, clusterName, rules) == expected
  }

  void "specific stack takes priority over wildcard detail"() {
    given:
    ClusterMatchRule expected = new ClusterMatchRule(account: account, location: location, stack: stack, detail: "*", priority: 2)
    List<ClusterMatchRule> rules = [
      new ClusterMatchRule(account: account, location: location, stack: "*", detail: detail, priority: 1),
      expected
    ]

    expect:
    ClusterMatcher.getMatchingRule(account, location, clusterName, rules) == expected
  }

  void "specific detail takes priority over priority"() {
    given:
    ClusterMatchRule expected = new ClusterMatchRule(account: account, location: location, stack: stack, detail: detail, priority: 2)
    List<ClusterMatchRule> rules = [
      new ClusterMatchRule(account: account, location: location, stack: stack, detail: "*", priority: 1),
      expected
    ]

    expect:
    ClusterMatcher.getMatchingRule(account, location, clusterName, rules) == expected
  }

  void "handles clusters without account or details values"() {
    given:
    ClusterMatchRule expected = new ClusterMatchRule(account: "*", location: "*", stack: "*", detail: "*", priority: 1)
    List<ClusterMatchRule> rules = [
      expected
    ]

    expect:
    ClusterMatcher.getMatchingRule(account, location, "myapp", rules) == expected
  }

  @Unroll
  void "handles rules without account or details values, preferring them to wildcards"() {
    given:
    List<ClusterMatchRule> rules = [
      new ClusterMatchRule(account: "*", location: "*", stack: "*", detail: "*", priority: 1),
      expected
    ]

    expect:
    ClusterMatcher.getMatchingRule(account, location, "myapp", rules) == expected

    where:
    expected << [
      new ClusterMatchRule(account: "*", location: "*", stack: null, detail: null, priority: 1),
      new ClusterMatchRule(account: "*", location: "*", stack: "", detail: "", priority: 1)
    ]
  }
}

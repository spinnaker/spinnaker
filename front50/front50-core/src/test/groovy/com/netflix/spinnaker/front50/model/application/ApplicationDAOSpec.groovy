/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.front50.model.application

import com.netflix.spinnaker.front50.model.SearchUtils
import spock.lang.Specification
import spock.lang.Unroll

class ApplicationDAOSpec extends Specification {
  def "should support sorting by a single attribute"() {
    given:
    def applications = [
      new Application(name: "YOUR APPLICATION"),
      new Application(name: "APPLICATION"),
      new Application(name: "MY APPLICATION"),
    ]

    expect:
    ApplicationDAO.Searcher.search(applications, ["name": "app"])*.name == [
      "APPLICATION", "MY APPLICATION", "YOUR APPLICATION"
    ]
  }

  def "should support sorting by multiple attributes"() {
    given:
    def applications = [
      new Application(name: "APPLICATION 123", description: "abcd"),
      new Application(name: "APPLICATION 1", description: "bcda"),
      new Application(name: "APPLICATION 12", description: "cdab"),
    ]

    expect:
    ApplicationDAO.Searcher.search(applications, ["name": "app", "description": "cd"])*.name == [
      "APPLICATION 1", "APPLICATION 12", "APPLICATION 123"
    ]
  }

  @Unroll
  def "should calculate correct single attribute scores"() {
    given:
    def application = new Application(applicationAttributes)

    expect:
    SearchUtils.score(application, attributeName, attributeValue) == score

    where:
    applicationAttributes                   | attributeName | attributeValue   || score
    ["name": "application"]                 | "name"        | "application"    || 111
    ["name": "application"]                 | "name"        | "app"            || 30
    ["name": "application"]                 | "name"        | "ppl"            || 29
    ["name": "application"]                 | "name"        | "ion"            || 28
    ["name": "application"]                 | "name"        | "does_not_match" || 0
  }

  @Unroll
  def "should calculate correct multiple attribute scores"() {
    given:
    def application = new Application(applicationAttributes)

    expect:
    SearchUtils.score(application, attributes) == score

    where:
    applicationAttributes                         | attributes                            || score
    ["name": "Netflix"]                           | ["name": "flix"]                      || 59
    ["name": "Netflix", description: "Spinnaker"] | ["name": "flix", description: "Spin"] || 107
    ["name": "Netflix", description: "Spinnaker"] | ["name": "flix", description: "ker"]  || 93
    ["name": "Netflix", description: "Spinnaker"] | ["name": "flix", owner: "netflix"]    || 59
  }


  @Unroll
  def "should be able to search applications"() {
    given:
    def apps = applications

    expect:
    ApplicationDAO.Searcher.search(apps, search)*.name == app

    where:
    applications                                                                                | search                                    | app
    [new Application(name: "APP1", details: [repoSlug: "app1"]), new Application(name: "APP2")] | [repoSlug: "app1"]                        | ["APP1"]
    [new Application(name: "APP1", details: [repoSlug: "app1"]), new Application(name: "APP2")] | [repoSlug: "APP1"]                        | ["APP1"]
    [new Application(name: "APP1", details: [repoSlug: "app1"]), new Application(name: "APP2")] | [repoSlug: "app2"]                        | []
    [new Application(name: "APP1", details: [repoSlug: "app1"]), new Application(name: "APP2")] | [name: "app2"]                            | ["APP2"]
    [new Application(name: "APP1", details: [repoSlug: "app1"]), new Application(name: "APP2")] | [:]                                       | ["APP1", "APP2"]
    [new Application(name: "APP1", details: [repoSlug: "app1", repoProjectKey: "org"])]         | [repoSlug: "app1", repoProjectKey: "org"] | ["APP1"]
    [new Application(name: "APP1", details: [repoSlug: "app1"])]                                | [reposlug: "app1"]                        | []
  }
}

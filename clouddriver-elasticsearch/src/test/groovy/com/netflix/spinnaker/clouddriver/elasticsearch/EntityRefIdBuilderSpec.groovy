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


package com.netflix.spinnaker.clouddriver.elasticsearch

import spock.lang.Specification
import spock.lang.Unroll

class EntityRefIdBuilderSpec extends Specification {

  def "should build identifiers of the form {{cloudProvider}}:{{entityType}}:{{entityId}}:{{account}}:{{region}}"() {
    expect:
    EntityRefIdBuilder.buildId("aws", "cluster", "front50-main", "prod", "us-west-1").id == "aws:cluster:front50-main:prod:us-west-1"
    EntityRefIdBuilder.buildId("aws", "cluster", "front50-main", null, "us-west-1").id == "aws:cluster:front50-main:*:us-west-1"
    EntityRefIdBuilder.buildId("aws", "cluster", "front50-main", null, null).id == "aws:cluster:front50-main:*:*"

    // generated identifiers should _always_ be lowercase
    EntityRefIdBuilder.buildId("AWS", "Cluster", "front50-MAIN", "PROD", "US-west-1").id == "aws:cluster:front50-main:prod:us-west-1"
  }

  @Unroll
  def "should require non-null '#nullFieldName'"() {
    when:
    EntityRefIdBuilder.buildId(cloudProvider, entityType, entityId, null, null)

    then:
    thrown(NullPointerException)

    where:
    cloudProvider | entityType | entityId       || nullFieldName
    null          | "cluster"  | "front50-main" || "cloudProvider"
    "aws"         | null       | "front50-main" || "entityType"
    "aws"         | "cluster"  | null           || "entityId"
  }
}

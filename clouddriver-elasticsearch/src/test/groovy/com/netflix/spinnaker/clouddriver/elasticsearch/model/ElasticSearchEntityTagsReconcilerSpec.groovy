/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.model

import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.model.ServerGroupProvider
import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.concurrent.TimeUnit;

class ElasticSearchEntityTagsReconcilerSpec extends Specification {
  def allEntityTags = [
    buildEntityTags("id-1", "aws", "servergroup", "clouddriver-main-v001", "myaccount", "us-west-1"),
    buildEntityTags("id-2", "aws", "servergroup", "clouddriver-main-v002", "myaccount", "us-west-2"),
    buildEntityTags("id-3", "aws", "servergroup", "clouddriver-main-v003", "myaccount", "us-west-1"),
    buildEntityTags("id-4", "k8s", "servergroup", "clouddriver-main-v004", "myaccount", "us-west-1"),
    buildEntityTags("id-5", "titus", "servergroup", "clouddriver-main-v005", "myaccount", "us-west-1"),
    buildEntityTags("id-6", "aws", "cluster", "clouddriver-main", "myaccount", "us-east-1"),
  ]

  def front50Service = Mock(Front50Service) {
    _ * getAllEntityTags(_) >> { return Calls.response(allEntityTags) }
  }

  def amazonServerGroupProvider = Mock(ServerGroupProvider) {
    _ * getCloudProviderId() >> { return "aws" }
    _ * buildServerGroupIdentifier(_, _, _) >> { String account, String region, String entityId ->
      return "aws:servergroups:${entityId}:${account}:${region}"
    }
  }

  def titusServerGroupProvider = Mock(ServerGroupProvider) {
    _ * getCloudProviderId() >> { return "titus" }
    _ * buildServerGroupIdentifier(_, _, _) >> { String account, String region, String entityId ->
      return "titus:servergroups:${entityId}:${account}:${region}"
    }
  }

  def entityTagsProvider = Mock(ElasticSearchEntityTagsProvider)


  @Shared
  def thirteenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(13)

  @Shared
  def fifteenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(15)

  @Subject
  def entityTagsReconciler = new ElasticSearchEntityTagsReconciler(
    front50Service,
    Optional.of([amazonServerGroupProvider, titusServerGroupProvider])
  )

  def "should build provider-specific identifier"() {
    expect:
    entityTagsReconciler.buildServerGroupIdentifier(
      buildEntityTags("id", "aws", "servergroup", "clouddriver-main-v003", "myaccount", "us-west-1").entityRef
    ) == "aws:servergroups:clouddriver-main-v003:myaccount:us-west-1"
  }

  def "should exclude any entity tags that reference a non-existent server group"() {
    when:
    def filteredEntityTags = entityTagsReconciler.filter(allEntityTags)

    then:
    1 * amazonServerGroupProvider.getServerGroupIdentifiers(null, null) >> {
      return [
        "aws:servergroups:clouddriver-main-v001:myaccount:us-west-1"
      ]
    }
    1 * titusServerGroupProvider.getServerGroupIdentifiers(null, null) >> {
      return [
        "titus:servergroups:clouddriver-main-v005:myaccount:us-west-1"
      ]
    }

    // entity tags for an unsupported cloud provider (k8s) or entity type (cluster) should _not_ be filtered out
    filteredEntityTags*.id == ["id-1", "id-4", "id-5", "id-6"]
  }

  @Unroll
  def "should reconcile and bulk delete any entity tags that reference a non-existent server group"() {
    given:
    allEntityTags.each { it.lastModified = 0 }

    when:
    def results = entityTagsReconciler.reconcile(entityTagsProvider, "aws", "myaccount", "us-west-1", dryRun)

    then:
    1 * amazonServerGroupProvider.getServerGroupIdentifiers(null, null) >> {
      return [
        "aws:servergroups:clouddriver-main-v001:myaccount:us-west-1"
      ]
    }

    (expectedBulkDelete ? 1 : 0) * entityTagsProvider.bulkDelete({ Collection<EntityTags> multipleEntityTags ->
      multipleEntityTags*.entityRef.entityId == ["clouddriver-main-v003"]
    })
    results.orphanCount == 1

    where:
    dryRun || expectedBulkDelete
    true   || false
    false  || true
  }

  @Unroll
  def "should filter entity tags by cloud provider, account and region"() {
    given:
    allEntityTags.each {
      it.setLastModified(lastModified)
    }

    when:
    def results = entityTagsReconciler.filter(allEntityTags, cloudProvider, account, region)*.entityRef.entityId

    then:
    results == expectedEntityTags

    where:
    cloudProvider | account     | region      | lastModified    || expectedEntityTags
    "none"        | null        | null        | thirteenDaysAgo || []
    "aws"         | null        | null        | thirteenDaysAgo || []
    "aws"         | "none"      | null        | fifteenDaysAgo  || []
    "aws"         | null        | "none"      | fifteenDaysAgo  || []
    "aws"         | null        | null        | fifteenDaysAgo  || ["clouddriver-main-v001", "clouddriver-main-v002", "clouddriver-main-v003"]
    "aws"         | "myaccount" | null        | fifteenDaysAgo  || ["clouddriver-main-v001", "clouddriver-main-v002", "clouddriver-main-v003"]
    "aws"         | "myaccount" | null        | fifteenDaysAgo  || ["clouddriver-main-v001", "clouddriver-main-v002", "clouddriver-main-v003"]
    "aws"         | "myaccount" | "us-west-1" | fifteenDaysAgo  || ["clouddriver-main-v001", "clouddriver-main-v003"]
  }

  private static EntityTags buildEntityTags(String id,
                                            String cloudProvider,
                                            String entityType,
                                            String entityId,
                                            String account,
                                            String region) {
    return new EntityTags(
      id: id,
      entityRef: new EntityTags.EntityRef(
        cloudProvider: cloudProvider,
        entityType: entityType,
        entityId: entityId,
        account: account,
        region: region
      )
    )
  }
}

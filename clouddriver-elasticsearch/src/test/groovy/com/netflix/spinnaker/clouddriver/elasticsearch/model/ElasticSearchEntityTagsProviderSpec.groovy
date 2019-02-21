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


package com.netflix.spinnaker.clouddriver.elasticsearch.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.config.ElasticSearchConfig
import com.netflix.spinnaker.config.ElasticSearchConfigProperties
import com.netflix.spinnaker.kork.core.RetrySupport
import io.searchbox.client.JestClient
import io.searchbox.indices.CreateIndex
import io.searchbox.indices.DeleteIndex
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.node.Node
import org.springframework.context.ApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Supplier

import static org.elasticsearch.node.NodeBuilder.nodeBuilder

class ElasticSearchEntityTagsProviderSpec extends Specification {
  @Shared
  Node node

  @Shared
  JestClient jestClient

  @Shared
  ElasticSearchConfigProperties elasticSearchConfigProperties

  RetrySupport retrySupport = Spy(RetrySupport) {
    _ * sleep(_) >> { /* do nothing */ }
  }

  ObjectMapper objectMapper = new ObjectMapper()
  Front50Service front50Service = Mock(Front50Service)
  ElasticSearchEntityTagsProvider entityTagsProvider
  ElasticSearchEntityTagsReconciler entityTagsReconciler = Mock(ElasticSearchEntityTagsReconciler)

  ApplicationContext applicationContext = Mock(ApplicationContext) {
    _ * getBean(_) >> { return entityTagsReconciler }
  }

  def setupSpec() {
    def elasticSearchSettings = Settings.settingsBuilder()
      .put("script.inline", "on")
      .put("script.indexed", "on")
      .put("path.data", "./es-tmp/es")
      .put("path.home", "./es-tmp/es")

    node = nodeBuilder()
      .local(true)
      .settings(elasticSearchSettings.build())
      .node()

    elasticSearchConfigProperties = new ElasticSearchConfigProperties(
      activeIndex: "tags_v1",
      connection: getConnectionString(node)
    )
    def config = new ElasticSearchConfig()
    jestClient = config.jestClient(elasticSearchConfigProperties)
  }

  def setup() {
    jestClient.execute(new DeleteIndex.Builder(elasticSearchConfigProperties.activeIndex).build());

    def settings = """{
  "settings": {
    "refresh_interval": "1s"
  },
  "mappings": {
    "_default_": {
      "properties": {
        "tags": {
          "type": "nested"
        },
        "entityRef": {
          "properties": {
            "entityId": {
              "type": "string",
              "index": "not_analyzed"
            }
          }
        }
      }
    }
  }
}"""

    jestClient.execute(new CreateIndex.Builder(elasticSearchConfigProperties.activeIndex)
      .settings(settings)
      .build());

    entityTagsProvider = new ElasticSearchEntityTagsProvider(
      applicationContext,
      retrySupport,
      objectMapper,
      front50Service,
      jestClient,
      elasticSearchConfigProperties
    )
  }

  def "should support single result retrieval by `EntityTags.id` and `EntityTags.tags`"() {
    given:
    def entityTags = buildEntityTags("aws:cluster:front50-main:myaccount:*", ["tag1": "value1", "tag2": "value2"])
    entityTagsProvider.index(entityTags)
    entityTagsProvider.verifyIndex(entityTags)

    expect:
    entityTagsProvider.get(entityTags.id).isPresent()
    !entityTagsProvider.get("does-not-exist").isPresent()

    entityTagsProvider.get(entityTags.id, ["tag1": "value1", "tag2": "value2"]).isPresent()
    !entityTagsProvider.get(entityTags.id, ["tag3": "value3"]).isPresent()
  }

  def "should assign entityRef.application if not specified"() {
    given:
    def entityTags = buildEntityTags("aws:cluster:front50-main:myaccount:*", ["tag1": "value1", "tag2": "value2"])
    entityTags.entityRef.application = application

    entityTagsProvider.index(entityTags)
    entityTagsProvider.verifyIndex(entityTags)

    expect:
    entityTagsProvider.get(entityTags.id).get().entityRef.application == expectedApplication

    where:
    application      || expectedApplication
    null             || "front50"
    ""               || "front50"
    " "              || "front50"
    "my_application" || "my_application"
  }

  def "should support multi result retrieval by `cloudProvider, `entityType`, `idPrefix` and `tags`"() {
    given:
    def entityTags = buildEntityTags("aws:cluster:clouddriver-main:myaccount:*", ["tag3": "value3"])
    entityTagsProvider.index(entityTags)
    entityTagsProvider.verifyIndex(entityTags)

    def moreEntityTags = buildEntityTags("aws:cluster:front50-main:myaccount:*", ["tag1": "value1"])
    entityTagsProvider.index(moreEntityTags)
    entityTagsProvider.verifyIndex(moreEntityTags)

    expect:
    // fetch everything
    entityTagsProvider.getAll(null, null, null, null, null, null, null, null, null, 2)*.id.sort() == [entityTags.id, moreEntityTags.id].sort()

    // fetch everything for an application
    entityTagsProvider.getAll(null, "front50", null, null, null, null, null, null, null, 2)*.id.sort() == [moreEntityTags.id].sort()

    // fetch everything for a single `entityId`
    entityTagsProvider.getAll(null, null, null, [entityTags.entityRef.entityId], null, null, null, null, null, 2)*.id.sort() == [entityTags.id].sort()

    // fetch everything for a multiple `entityId`
    entityTagsProvider.getAll(null, null, null, [
      entityTags.entityRef.entityId, moreEntityTags.entityRef.entityId
    ], null, null, null, null, null, 2)*.id.sort() == [entityTags.id, moreEntityTags.id].sort()

    // fetch everything for `cloudprovider`
    entityTagsProvider.getAll("aws", null, null, null, null, null, null, null, null, 2)*.id.sort() == [entityTags.id, moreEntityTags.id].sort()

    // fetch everything for `cloudprovider` and `cluster`
    entityTagsProvider.getAll("aws", null, "cluster", null, null, null, null, null, null, 2)*.id.sort() == [entityTags.id, moreEntityTags.id].sort()

    // fetch everything for `cloudprovider`, `cluster` and `idPrefix`
    entityTagsProvider.getAll("aws", null, "cluster", null, "aws:cluster:clouddriver*", null, null, null, null, 2)*.id == [entityTags.id]

    // fetch everything for `cloudprovider`, `cluster`, `idPrefix` and `tags`
    entityTagsProvider.getAll("aws", null, "cluster", null, "aws*", null, null, null, ["tag3": "value3"], 2)*.id == [entityTags.id]

    // verify that globbing by tags works (with and without a namespace specified)
    entityTagsProvider.getAll("aws", null, "cluster", null, "aws*", null, null, "default", ["tag3": "*"], 2)*.id == [entityTags.id]
    entityTagsProvider.getAll("aws", null, "cluster", null, "aws*", null, null, null, ["tag3": "*"], 2)*.id == [entityTags.id]

    // namespace 'not_default' does not exist and should negate the matched tags
    entityTagsProvider.getAll("aws", null, "cluster", null, "aws*", null, null, "not_default", ["tag3": "*"], 2).isEmpty()

    // verify that `maxResults` works
    entityTagsProvider.getAll("aws", null, "cluster", null, null, null, null, null, null, 0).isEmpty()

  }

  @Unroll
  def "should flatten a nested map"() {
    expect:
    entityTagsProvider.flatten([:], null, source) == flattened

    where:
    source                               || flattened
    ["a": "b"]                           || ["a": "b"]
    ["a": ["b": ["c"]]]                  || ["a.b": ["c"]]
    ["a": ["b": ["c": ["d"]]]]           || ["a.b.c": ["d"]]
    ["a": ["b": ["c": ["d"]]], "e": "f"] || ["a.b.c": ["d"], "e": "f"]
  }

  def "should filter entity tags when performing a reindex"() {
    given:
    def allEntityTags = [
      buildEntityTags("aws:servergroup:clouddriver-main-v001:myaccount:us-west-1", [:]),
      buildEntityTags("aws:servergroup:clouddriver-main-v002:myaccount:us-west-1", [:]),
    ]

    when:
    entityTagsProvider.reindex()

    then:
    1 * front50Service.getAllEntityTags(true) >> { return allEntityTags }
    1 * entityTagsReconciler.filter(allEntityTags) >> { return [ allEntityTags[1] ] }

    entityTagsProvider.verifyIndex(allEntityTags[1])
    !entityTagsProvider.get(allEntityTags[0].id).isPresent()
  }

  def "should delete multiple entity tags (bulk)"() {
    given:
    def allEntityTags = [
      buildEntityTags("aws:servergroup:clouddriver-main-v001:myaccount:us-west-1", [:]),
      buildEntityTags("aws:servergroup:clouddriver-main-v002:myaccount:us-west-1", [:]),
      buildEntityTags("aws:servergroup:clouddriver-main-v003:myaccount:us-west-1", [:]),
    ]
    allEntityTags.each {
      entityTagsProvider.index(it)
      entityTagsProvider.verifyIndex(it)
    }

    when:
    entityTagsProvider.bulkDelete(allEntityTags)

    then:
    verifyNotIndexed(allEntityTags[0])
    verifyNotIndexed(allEntityTags[1])
    verifyNotIndexed(allEntityTags[2])
  }

  def "should delete all entity tags in namespace"() {
    given:
    def allEntityTags = [
      buildEntityTags("aws:servergroup:clouddriver-main-v001:myaccount:us-west-1", ["a": "1"], "my_namespace"),
      buildEntityTags("aws:servergroup:clouddriver-main-v002:myaccount:us-west-1", ["b": "2"], "my_namespace"),
      buildEntityTags("aws:servergroup:clouddriver-main-v003:myaccount:us-west-1", ["c": "3"]),
    ]
    allEntityTags.each {
      entityTagsProvider.index(it)
      entityTagsProvider.verifyIndex(it)
    }

    when:
    entityTagsProvider.deleteByNamespace("my_namespace", true, false) // dry-run

    then:
    1 * front50Service.getAllEntityTags(false) >> {
      return entityTagsProvider.getAll(
        null, null, null, null, null, null, null, null, [:], 100
      )
    }
    0 * _

    when:
    entityTagsProvider.deleteByNamespace("my_namespace", true, true) // dry-run

    then:
    1 * front50Service.getAllEntityTags(false) >> {
      return entityTagsProvider.getAll(
        null, null, null, null, null, null, null, null, [:], 100
      )
    }
    0 * _

    when:
    entityTagsProvider.deleteByNamespace("my_namespace", false, false) // remove from elasticsearch (only!)
    Thread.sleep(1000)

    def allIndexedEntityTags = entityTagsProvider.getAll(
      null, null, null, null, null, null, null, null, [:], 100
    )

    then:
    1 * front50Service.getAllEntityTags(false) >> {
      return entityTagsProvider.getAll(
        null, null, null, null, null, null, null, null, [:], 100
      )
    }
    _ * retrySupport.retry(_, _, _, _) >> { Supplier fn, int maxRetries, long retryBackoff, boolean exponential -> fn.get() }
    0 * _

    allIndexedEntityTags.findAll {
      it.tags.any { it.namespace == "my_namespace"}
    }.isEmpty()

    when:
    entityTagsProvider.deleteByNamespace("my_namespace", false, true) // remove from elasticsearch and front50

    then:
    1 * front50Service.getAllEntityTags(false) >> { return allEntityTags }
    1 * front50Service.batchUpdate(_)
    _ * retrySupport.retry(_, _, _, _) >> { Supplier fn, int maxRetries, long retryBackoff, boolean exponential -> fn.get() }
    0 * _
  }

  boolean verifyNotIndexed(EntityTags entityTags) {
    return (1..5).any {
      if (!entityTagsProvider.get(entityTags.id).isPresent()) {
        return true
      }

      Thread.sleep(500)
      return false
    }
  }

  private static EntityTags buildEntityTags(String id, Map<String, String> tags, String namespace = "default") {
    def idSplit = id.split(":")
    return new EntityTags(
      id: id,
      tags: tags.collect { k, v ->
        new EntityTags.EntityTag(name: k, value: v, valueType: EntityTags.EntityTagValueType.literal, namespace: namespace)
      },
      entityRef: new EntityTags.EntityRef(
        entityType: idSplit[1],
        cloudProvider: idSplit[0],
        entityId: idSplit[2]
      )
    )
  }

  // The Node object does not store its connection string, so we need to make a request to the cluster
  // to get it using the Node's client.
  private static String getConnectionString(Node node) {
    def nodeName = node.settings().get("name")
    def nodeInfoResponse = node.client().admin().cluster().prepareNodesInfo(nodeName).execute().get()
    return "http://" + nodeInfoResponse[0].serviceAttributes.http_address
  }
}

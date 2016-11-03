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
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.config.ElasticSearchConfig
import com.netflix.spinnaker.config.ElasticSearchConfigProperties
import io.searchbox.client.JestClient
import io.searchbox.indices.CreateIndex
import io.searchbox.indices.DeleteIndex
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.node.Node
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static org.elasticsearch.node.NodeBuilder.nodeBuilder

class ElasticSearchEntityTagsProviderSpec extends Specification {
  @Shared
  Node node

  @Shared
  JestClient jestClient

  @Shared
  ObjectMapper objectMapper = new ObjectMapper()

  @Shared
  ElasticSearchConfigProperties elasticSearchConfigProperties

  @Shared
  ElasticSearchEntityTagsProvider entityTagsProvider

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
      connection: "http://localhost:9200"
    )
    def config = new ElasticSearchConfig()
    jestClient = config.jestClient(elasticSearchConfigProperties)

    entityTagsProvider = new ElasticSearchEntityTagsProvider(
      objectMapper,
      jestClient,
      elasticSearchConfigProperties
    )
  }

  def setup() {
    jestClient.execute(new DeleteIndex.Builder(elasticSearchConfigProperties.activeIndex).build());

    Settings.Builder settingsBuilder = Settings.settingsBuilder();
    settingsBuilder.put("refresh_interval", "1s")

    jestClient.execute(new CreateIndex.Builder(elasticSearchConfigProperties.activeIndex)
      .settings(settingsBuilder.build())
      .build());
  }

  def "should support single result retrieval by `EntityTags.id` and `EntityTags.tags`"() {
    given:
    def entityTags = buildEntityTags("aws:cluster:front50-main:myaccount:*", ["tag1": "value1", "tag2": "value2"])
    entityTagsProvider.index(entityTags)
    entityTagsProvider.verifyIndex(entityTags)

    expect:
    entityTagsProvider.get(entityTags.id).isPresent()
    !entityTagsProvider.get("does-not-exist").isPresent()

    entityTagsProvider.get(entityTags.id, entityTags.tags).isPresent()
    !entityTagsProvider.get(entityTags.id, ["tag3": "value3"]).isPresent()
  }

  def "should support multi result retrieval by `cloudProvider, `entityType`, `idPrefix` and `tags`"() {
    given:
    def entityTags = buildEntityTags("aws:cluster:clouddriver-main:myaccount:*", ["tag3": "value3"])
    entityTagsProvider.index(entityTags)
    entityTagsProvider.verifyIndex(entityTags)

    expect:
    // fetch everything
    entityTagsProvider.getAll(null, null, null, null, 2)*.id == [entityTags.id]

    // fetch everything for `cloudprovider`
    entityTagsProvider.getAll("aws", null, null, null, 2)*.id == [entityTags.id]

    // fetch everything for `cloudprovider` and `cluster`
    entityTagsProvider.getAll("aws", "cluster", null, null, 2)*.id == [entityTags.id]

    // fetch everything for `cloudprovider`, `cluster` and `idPrefix`
    entityTagsProvider.getAll("aws", "cluster", "aws:cluster:clouddriver*", null, 2)*.id == [entityTags.id]

    // fetch everything for `cloudprovider`, `cluster`, `idPrefix` and `tags`
    entityTagsProvider.getAll("aws", "cluster", "aws*", ["tag3": "value3"], 2)*.id == [entityTags.id]

    // verify that globbing by tags works
    entityTagsProvider.getAll("aws", "cluster", "aws*", ["tag3": "*"], 2)*.id == [entityTags.id]

    // verify that `maxResults` works
    entityTagsProvider.getAll("aws", "cluster", null, null, 0).isEmpty()
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

  private static EntityTags buildEntityTags(String id, Map<String, String> tags) {
    return new EntityTags(
      id: id,
      tags: tags,
      entityRef: new EntityTags.EntityRef(entityType: "cluster", cloudProvider: "aws")
    )
  }
}

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

package com.netflix.spinnaker.clouddriver.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchException
import com.netflix.spinnaker.clouddriver.elasticsearch.model.Model
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ModelType
import io.searchbox.client.JestClient
import io.searchbox.core.Bulk
import io.searchbox.core.BulkResult
import io.searchbox.core.Index
import io.searchbox.indices.CreateIndex
import io.searchbox.indices.DeleteIndex
import io.searchbox.indices.aliases.AddAliasMapping
import io.searchbox.indices.aliases.GetAliases
import io.searchbox.indices.aliases.ModifyAliases
import java.io.IOException

class ElasticSearchClient(private val objectMapper: ObjectMapper, private val jestClient: JestClient) {
  fun getPreviousIndexes(prefix: String): Set<String> {
    try {
      val result = jestClient.execute(GetAliases.Builder().build())
      val r = objectMapper.readValue(result.jsonString, Map::class.java) as Map<String, Any>
      return r.keys.filter { k -> k.startsWith(prefix) }.toSet()
    } catch (e: IOException) {
      throw ElasticSearchException("Unable to fetch previous indexes (prefix: $prefix)", e)
    }
  }

  fun createIndex(prefix: String): String {
    val newIndexName = "${prefix}_${System.currentTimeMillis()}"

    try {
      jestClient.execute(CreateIndex.Builder(newIndexName).build())
      return newIndexName
    } catch (e: IOException) {
      throw ElasticSearchException("Unable to create index (index: $newIndexName)", e)
    }
  }

  fun createAlias(index: String, alias: String) {
    try {
      jestClient.execute(
        ModifyAliases.Builder(
          AddAliasMapping.Builder(index, alias).build()
        ).build()
      )
    } catch (e: IOException) {
      throw ElasticSearchException("Unable to create alias (index: $index, alias: $alias)", e)
    }
  }

  fun deleteIndex(index: String) {
    try {
      jestClient.execute(
        DeleteIndex.Builder(index).build()
      )
    } catch (e: IOException) {
      throw ElasticSearchException("Unable to delete index (index: $index)", e)
    }
  }

  fun <T : Model> store(index: String, type: ModelType, partition: List<T>) {
    var builder: Bulk.Builder = Bulk.Builder().defaultIndex(index)

    for (serverGroupModel in partition) {
      builder = builder.addAction(
        Index.Builder(objectMapper.convertValue(serverGroupModel, Map::class.java))
          .index(index)
          .type(type.toString())
          .id(serverGroupModel.id)
          .build()
      )
    }

    val bulk = builder.build()
    try {
      val jestResult = jestClient.execute<BulkResult>(bulk)
      if (!jestResult.isSucceeded) {
        throw ElasticSearchException(
          java.lang.String.format("Failed to index server groups, reason: '%s'", jestResult.getErrorMessage())
        )
      }
    } catch (e: IOException) {
      throw ElasticSearchException(
        java.lang.String.format("Failed to index server groups, reason: '%s'", e.message)
      )
    }
  }
}

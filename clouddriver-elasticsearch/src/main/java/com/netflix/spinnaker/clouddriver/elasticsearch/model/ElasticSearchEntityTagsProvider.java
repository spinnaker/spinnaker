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

package com.netflix.spinnaker.clouddriver.elasticsearch.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider;
import com.netflix.spinnaker.config.ElasticSearchConfigProperties;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Delete;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Component
public class ElasticSearchEntityTagsProvider implements EntityTagsProvider {
  private final ObjectMapper objectMapper;
  private final JestClient jestClient;
  private final String activeElasticSearchIndex;

  @Autowired
  public ElasticSearchEntityTagsProvider(ObjectMapper objectMapper,
                                         JestClient jestClient,
                                         ElasticSearchConfigProperties elasticSearchConfigProperties) {
    this.objectMapper = objectMapper;
    this.jestClient = jestClient;
    this.activeElasticSearchIndex = elasticSearchConfigProperties.getActiveIndex();
  }

  @Override
  public Collection<EntityTags> getAll(String cloudProvider,
                                       String entityType,
                                       String idPrefix,
                                       Map<String, Object> tags,
                                       int maxResults) {
    BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

    if (cloudProvider != null) {
      // restrict to a specific clouddriver (optional)
      queryBuilder = queryBuilder.must(QueryBuilders.matchQuery("entityRef.cloudProvider", cloudProvider));
    }

    if (idPrefix != null) {
      // restrict to a specific id prefix (optional)
      queryBuilder = queryBuilder.must(QueryBuilders.wildcardQuery("id", idPrefix));
    }

    return search(entityType, applyTagsToBuilder(queryBuilder, tags), maxResults);
  }

  @Override
  public Optional<EntityTags> get(String id) {
    return get(id, Collections.emptyMap());
  }

  @Override
  public Optional<EntityTags> get(String id, Map<String, Object> tags) {
    BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("_id", id));
    List<EntityTags> entityTags = search(null, applyTagsToBuilder(queryBuilder, tags), 1);
    return entityTags.isEmpty() ? Optional.empty() : Optional.of(entityTags.get(0));
  }

  @Override
  public void index(EntityTags entityTags) {
    try {
      Index action = new Index.Builder(objectMapper.convertValue(entityTags, Map.class))
        .index(activeElasticSearchIndex)
        .type(entityTags.getEntityRef().getEntityType())
        .id(entityTags.getId())
        .build();

      JestResult jestResult = jestClient.execute(action);
      if (!jestResult.isSucceeded()) {
        throw new ElasticSearchException(
          format("Failed to index %s, reason: '%s'", entityTags.getId(), jestResult.getErrorMessage())
        );
      }
    } catch (IOException e) {
      throw new ElasticSearchException(
        format("Failed to index %s, reason: '%s'", entityTags.getId(), e.getMessage())
      );
    }
  }

  @Override
  public void delete(String id) {
    try {
      EntityTags entityTags = get(id).orElse(null);
      if (entityTags == null) {
        // EntityTags w/ id = :id does not actually exist
        return;
      }

      Delete action = new Delete.Builder(id)
        .index(activeElasticSearchIndex)
        .type(entityTags.getEntityRef().getEntityType())
        .build();

      JestResult jestResult = jestClient.execute(action);
      if (!jestResult.isSucceeded()) {
        throw new ElasticSearchException(
          format("Failed to delete %s, reason: '%s'", id, jestResult.getErrorMessage())
        );
      }
    } catch (IOException e) {
      throw new ElasticSearchException(
        format("Failed to delete %s, reason: '%s'", id, e.getMessage())
      );
    }
  }

  @Override
  public void verifyIndex(EntityTags entityTags) {
    OperationPoller.retryWithBackoff(o -> {
        // verify that the indexed document can be retrieved (accounts for index lag)
        if (!get(entityTags.getId(), entityTags.getTags()).isPresent()) {
          throw new ElasticSearchException(format("Failed to index %s, reason: 'no document found with id'", entityTags.getId()));
        }
        return true;
      },
      1000,
      3
    );
  }

  private BoolQueryBuilder applyTagsToBuilder(BoolQueryBuilder queryBuilder, Map<String, Object> tags) {
    if (tags == null) {
      return queryBuilder;
    }

    for (Map.Entry<String, Object> entry : flatten(new HashMap<>(), null, tags).entrySet()) {
      // restrict to specific tags (optional)
      if (entry.getValue().equals("*")) {
        queryBuilder = queryBuilder.must(QueryBuilders.existsQuery("tags." + entry.getKey()));
      } else {
        queryBuilder = queryBuilder.must(QueryBuilders.matchQuery("tags." + entry.getKey(), entry.getValue()));
      }
    }

    return queryBuilder;
  }

  /**
   * Elasticsearch requires that all search criteria be flattened (vs. nested)
   */
  private Map<String,Object> flatten(Map<String, Object> accumulator, String rootKey, Map<String, Object> criteria) {
    criteria.forEach((k, v) -> {
        if (v instanceof Map) {
          flatten(accumulator, (rootKey == null) ? "" + k : rootKey + "." + k, (Map) v);
        } else {
          accumulator.put((rootKey == null) ? "" + k : rootKey + "." + k, v);
        }
      }
    );

    return accumulator;
  }

  private List<EntityTags> search(String type, QueryBuilder queryBuilder, int maxResults) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(queryBuilder).size(maxResults);
    String searchQuery = searchSourceBuilder.toString();

    Search.Builder searchBuilder = new Search.Builder(searchQuery).addIndex(activeElasticSearchIndex);
    if (type != null) {
      // restrict to a specific index type (optional)
      searchBuilder.addType(type);
    }

    try {
      SearchResult searchResult = jestClient.execute(searchBuilder.build());
      return searchResult.getHits(Map.class).stream()
        .map(h -> h.source)
        .map(s -> objectMapper.convertValue(s, EntityTags.class))
        .collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

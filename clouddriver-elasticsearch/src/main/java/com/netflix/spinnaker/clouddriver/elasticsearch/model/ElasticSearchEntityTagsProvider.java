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
import com.google.common.collect.Lists;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider;
import com.netflix.spinnaker.config.ElasticSearchConfigProperties;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Bulk;
import io.searchbox.core.Delete;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.DeleteIndex;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
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
  private static final Logger log = LoggerFactory.getLogger(ElasticSearchEntityTagsProvider.class);

  private final ObjectMapper objectMapper;
  private final Front50Service front50Service;
  private final JestClient jestClient;
  private final String activeElasticSearchIndex;

  @Autowired
  public ElasticSearchEntityTagsProvider(ObjectMapper objectMapper,
                                         Front50Service front50Service,
                                         JestClient jestClient,
                                         ElasticSearchConfigProperties elasticSearchConfigProperties) {
    this.objectMapper = objectMapper;
    this.front50Service = front50Service;
    this.jestClient = jestClient;
    this.activeElasticSearchIndex = elasticSearchConfigProperties.getActiveIndex();
  }

  @Override
  public Collection<EntityTags> getAll(String cloudProvider,
                                       String entityType,
                                       List<String> entityIds,
                                       String idPrefix,
                                       String account,
                                       String region,
                                       String namespace,
                                       Map<String, Object> tags,
                                       int maxResults) {
    BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

    if (cloudProvider != null) {
      // restrict to a specific cloudProvider (optional)
      queryBuilder = queryBuilder.must(QueryBuilders.termQuery("entityRef.cloudProvider", cloudProvider));
    }

    if (entityIds != null && !entityIds.isEmpty()) {
      // restrict to a specific set of entityIds (optional)
     queryBuilder = queryBuilder.must(QueryBuilders.termsQuery("entityRef.entityId", entityIds));
    }

    if (account != null) {
      // restrict to a specific set of entityIds (optional)
      queryBuilder = queryBuilder.must(QueryBuilders.termQuery("entityRef.account", account));
    }

    if (region != null) {
      // restrict to a specific set of entityIds (optional)
      queryBuilder = queryBuilder.must(QueryBuilders.termQuery("entityRef.region", region));
    }

    if (idPrefix != null) {
      // restrict to a specific id prefix (optional)
      queryBuilder = queryBuilder.must(QueryBuilders.wildcardQuery("id", idPrefix));
    }

    if (tags != null) {
      for (Map.Entry<String, Object> entry : tags.entrySet()) {
        // each key/value pair maps to a distinct nested `tags` object and must be a unique query snippet
        queryBuilder = queryBuilder.must(
          applyTagsToBuilder(namespace, Collections.singletonMap(entry.getKey(), entry.getValue()))
        );
      }
    }

    if ((tags == null || tags.isEmpty()) && namespace != null) {
      // this supports a search akin to /tags?namespace=my_namespace which should return all entities with _any_ tag in
      // the given namespace ... ensures that the namespace filter is applied even if no tag criteria provided
      queryBuilder = queryBuilder.must(
        applyTagsToBuilder(namespace, Collections.emptyMap())
      );
    }

    return search(entityType, queryBuilder, maxResults);
  }

  @Override
  public Optional<EntityTags> get(String id) {
    return get(id, Collections.emptyMap());
  }

  @Override
  public Optional<EntityTags> get(String id, Map<String, Object> tags) {
    BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery().must(QueryBuilders.matchQuery("_id", id));
    if (tags != null) {
      for (Map.Entry<String, Object> entry: tags.entrySet()) {
        // each key/value pair maps to a distinct nested `tags` object and must be a unique query snippet
        queryBuilder = queryBuilder.must(
          applyTagsToBuilder(null, Collections.singletonMap(entry.getKey(), entry.getValue()))
        );
      }
    }

    List<EntityTags> entityTags = search(null, queryBuilder, 1);
    return entityTags.isEmpty() ? Optional.empty() : Optional.of(entityTags.get(0));
  }

  @Override
  public void index(EntityTags entityTags) {
    try {
      Index action = new Index.Builder(objectMapper.convertValue(prepareForWrite(objectMapper, entityTags), Map.class))
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
  public void bulkIndex(Collection<EntityTags> multipleEntityTags) {
    Lists.partition(new ArrayList<>(multipleEntityTags), 1000).forEach(tags -> {
      Bulk.Builder builder = new Bulk.Builder()
        .defaultIndex(activeElasticSearchIndex);

      for (EntityTags entityTags : tags) {
        builder = builder.addAction(
          new Index.Builder(objectMapper.convertValue(prepareForWrite(objectMapper, entityTags), Map.class))
            .index(activeElasticSearchIndex)
            .type(entityTags.getEntityRef().getEntityType())
            .id(entityTags.getId())
            .build()
        );
      }

      Bulk bulk = builder.build();
      try {
        JestResult jestResult = jestClient.execute(bulk);
        if (!jestResult.isSucceeded()) {
          throw new ElasticSearchException(
            format("Failed to index bulk entity tags, reason: '%s'", jestResult.getErrorMessage())
          );
        }
      } catch (IOException e) {
        throw new ElasticSearchException(
          format("Failed to index bulk entity tags, reason: '%s'", e.getMessage())
        );
      }
    });
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
  public void reindex() {
    try {
      log.info("Deleting Index {}", activeElasticSearchIndex);
      jestClient.execute(
        new DeleteIndex.Builder(activeElasticSearchIndex).build()
      );
      log.info("Deleted Index {}", activeElasticSearchIndex);

      log.info("Creating Index {}", activeElasticSearchIndex);
      jestClient.execute(
        new CreateIndex.Builder(activeElasticSearchIndex).build()
      );
      log.info("Created Index {}", activeElasticSearchIndex);
    } catch (IOException e) {
      throw new ElasticSearchException("Unable to re-create index '" + activeElasticSearchIndex + "'");
    }

    Collection<EntityTags> entityTags = front50Service.getAllEntityTags();

    log.info("Indexing {} entity tags", entityTags.size());
    bulkIndex(
      entityTags
      .stream()
      .filter(e -> e.getEntityRef() != null)
      .collect(Collectors.toList())
    );
    log.info("Indexed {} entity tags", entityTags.size());
  }

  @Override
  public void verifyIndex(EntityTags entityTags) {
    OperationPoller.retryWithBackoff(o -> {
        // verify that the indexed document can be retrieved (accounts for index lag)
        Map<String, Object> entityTagsCriteria = new HashMap<>();
        entityTags.getTags().stream().filter(entityTag -> entityTag != null && entityTag.getValueType() != null).forEach(entityTag -> {
          switch(entityTag.getValueType()) {
            case object:
              entityTagsCriteria.put(entityTag.getName(), "*");
              break;
            default:
              entityTagsCriteria.put(entityTag.getName(), entityTag.getValueForRead(objectMapper));
          }
        });

        if (!get(entityTags.getId(), entityTagsCriteria).isPresent()) {
          throw new ElasticSearchException(format("Failed to index %s, reason: 'no document found with id'", entityTags.getId()));
        }
        return true;
      },
      1000,
      3
    );
  }

  private QueryBuilder applyTagsToBuilder(String namespace, Map<String, Object> tags) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

    for (Map.Entry<String, Object> entry : flatten(new HashMap<>(), null, tags).entrySet()) {
      // restrict to specific tags (optional)
      boolQueryBuilder.must(QueryBuilders.termQuery("tags.name", entry.getKey()));
      if (!entry.getValue().equals("*")) {
        boolQueryBuilder.must(QueryBuilders.matchQuery("tags.value", entry.getValue()));
      }
    }

    if (namespace != null) {
      boolQueryBuilder.must(QueryBuilders.termQuery("tags.namespace", namespace));
    }

    return QueryBuilders.nestedQuery("tags", boolQueryBuilder);
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
      searchBuilder.addType(type.toLowerCase());
    }

    try {
      SearchResult searchResult = jestClient.execute(searchBuilder.build());
      return searchResult.getHits(Map.class).stream()
        .map(h -> h.source)
        .map(s -> prepareForRead(objectMapper, s))
        .collect(Collectors.toList());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static EntityTags prepareForWrite(ObjectMapper objectMapper, EntityTags entityTags) {
    EntityTags copyOfEntityTags = objectMapper.convertValue(
      objectMapper.convertValue(entityTags, Map.class), EntityTags.class
    );

    copyOfEntityTags.getTags().forEach(entityTag -> entityTag.setValue(entityTag.getValueForWrite(objectMapper)));

    return copyOfEntityTags;
  }

  private static EntityTags prepareForRead(ObjectMapper objectMapper, Map indexedEntityTags) {
    EntityTags entityTags = objectMapper.convertValue(indexedEntityTags, EntityTags.class);
    entityTags.getTags().forEach(entityTag -> entityTag.setValue(entityTag.getValueForRead(objectMapper)));

    return entityTags;
  }
}

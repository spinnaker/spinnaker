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
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider;
import com.netflix.spinnaker.config.ElasticSearchConfigProperties;
import com.netflix.spinnaker.kork.core.RetrySupport;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Bulk;
import io.searchbox.core.ClearScroll;
import io.searchbox.core.Delete;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.SearchScroll;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.DeleteIndex;
import io.searchbox.params.Parameters;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Component
public class ElasticSearchEntityTagsProvider implements EntityTagsProvider {
  private static final Logger log = LoggerFactory.getLogger(ElasticSearchEntityTagsProvider.class);

  private final ApplicationContext applicationContext;
  private final RetrySupport retrySupport;
  private final ObjectMapper objectMapper;
  private final Front50Service front50Service;
  private final JestClient jestClient;

  private final String activeElasticSearchIndex;

  private final boolean singleMappingType;
  private final String mappingTypeName;


  @Autowired
  public ElasticSearchEntityTagsProvider(ApplicationContext applicationContext,
                                         RetrySupport retrySupport,
                                         ObjectMapper objectMapper,
                                         Front50Service front50Service,
                                         JestClient jestClient,
                                         ElasticSearchConfigProperties elasticSearchConfigProperties) {
    this.applicationContext = applicationContext;
    this.retrySupport = retrySupport;
    this.objectMapper = objectMapper;
    this.front50Service = front50Service;
    this.jestClient = jestClient;
    this.activeElasticSearchIndex = elasticSearchConfigProperties.getActiveIndex();
    this.singleMappingType = elasticSearchConfigProperties.isSingleMappingType();
    this.mappingTypeName = elasticSearchConfigProperties.getMappingTypeName();
  }

  @Override
  public Collection<EntityTags> getAll(String cloudProvider,
                                       String application,
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

    if (application != null) {
      // restrict to a specific application (optional)
      queryBuilder = queryBuilder.must(QueryBuilders.termQuery("entityRef.application", application));
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
      for (Map.Entry<String, Object> entry : tags.entrySet()) {
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
        .type(getDocumentType(entityTags))
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
            .type(getDocumentType(entityTags))
            .id(entityTags.getId())
            .build()
        );
      }

      Bulk bulk = builder.build();
      retrySupport.retry(
        () -> {
          try {
            JestResult jestResult = jestClient.execute(bulk);
            if (!jestResult.isSucceeded()) {
              throw new ElasticSearchException(
                format("Failed to index bulk entity tags, reason: '%s'", jestResult.getErrorMessage())
              );
            }
            return true;
          } catch (IOException e) {
            String message = format("Failed to index bulk entity tags, reason: '%s'", e.getMessage());
            log.error(message + " ... retrying!");
            throw new ElasticSearchException(message);
          }
        },
        5, 1000, false);
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
        .type(getDocumentType(entityTags))
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
  public void bulkDelete(Collection<EntityTags> multipleEntityTags) {
    Lists.partition(new ArrayList<>(multipleEntityTags), 1000).forEach(tags -> {
      Bulk.Builder builder = new Bulk.Builder()
        .defaultIndex(activeElasticSearchIndex);

      for (EntityTags entityTags : tags) {
        builder = builder.addAction(
          new Delete.Builder(entityTags.getId())
            .type(getDocumentType(entityTags))
            .build()
        );
      }

      Bulk bulk = builder.build();
      try {
        JestResult jestResult = jestClient.execute(bulk);
        if (!jestResult.isSucceeded()) {
          throw new ElasticSearchException(
            format("Failed to bulk delete entity tags, reason: '%s'", jestResult.getErrorMessage())
          );
        }
      } catch (IOException e) {
        throw new ElasticSearchException(
          format("Failed to bulk delete entity tags, reason: '%s'", e.getMessage())
        );
      }
    });
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

    Collection<EntityTags> entityTags = front50Service.getAllEntityTags(true);
    Collection<EntityTags> filteredEntityTags = getElasticSearchEntityTagsReconciler().filter(entityTags);

    log.info(
      "Indexing {} entity tags ({} orphans have been excluded)",
      filteredEntityTags.size(),
      entityTags.size() - filteredEntityTags.size()
    );

    bulkIndex(
      filteredEntityTags
        .stream()
        .filter(e -> e.getEntityRef() != null)
        .collect(Collectors.toList())
    );

    log.info("Indexed {} entity tags", filteredEntityTags.size());
  }

  @Override
  public Map delta() {
    Collection<EntityTags> allEntityTagsFront50 = front50Service.getAllEntityTags(false);
    Map<String, List<EntityTags>> entityTagsByEntityTypeFront50 = allEntityTagsFront50
      .stream()
      .collect(Collectors.groupingBy(e ->
          Optional.ofNullable(
            Optional.ofNullable(e.getEntityRef()).orElse(new EntityTags.EntityRef()).getEntityType()
          ).orElse("unknown")
        )
      );

    Map<String, List<EntityTags>> entityTagsByEntityTypeElasticsearch = new HashMap<>();
    entityTagsByEntityTypeFront50.keySet().forEach(entityType ->
      entityTagsByEntityTypeElasticsearch.put(
        entityType,
        fetchAll(QueryBuilders.matchAllQuery(), entityType, 5000, "2m")
      )
    );

    Map<String, Map> metadata = new HashMap<>();

    entityTagsByEntityTypeFront50.keySet().forEach(entityType -> {
      Map<String, Object> entityTypeMetadata = new HashMap<>();
      metadata.put(entityType, entityTypeMetadata);

      Set<String> entityIdsFront50 = entityTagsByEntityTypeFront50.get(entityType).stream()
        .map(EntityTags::getId)
        .collect(Collectors.toSet());

      Set<String> entityIdsElasticsearch = entityTagsByEntityTypeElasticsearch.get(entityType).stream()
        .map(EntityTags::getId)
        .collect(Collectors.toSet());

      entityTypeMetadata.put("front50_count", entityIdsFront50.size());
      entityTypeMetadata.put("elasticsearch_count", entityIdsElasticsearch.size());

      if (!entityIdsFront50.equals(entityIdsElasticsearch)) {
        Set<String> entityIdsMissingInFront50 = entityIdsElasticsearch.stream()
          .filter(e -> !entityIdsFront50.contains(e))
          .collect(Collectors.toSet());

        Set<String> entityIdsMissingInElasticsearch = entityIdsFront50.stream()
          .filter(e -> !entityIdsElasticsearch.contains(e))
          .collect(Collectors.toSet());

        log.warn("'{}' missing in Front50 ({}) {}", entityType, entityIdsMissingInFront50.size(), entityIdsMissingInFront50);
        log.warn("'{}' missing in Elasticsearch ({}) {}", entityType, entityIdsMissingInElasticsearch.size(), entityIdsMissingInElasticsearch);

        entityTypeMetadata.put("front50_missing", entityIdsMissingInFront50);
        entityTypeMetadata.put("front50_missing_count", entityIdsMissingInFront50.size());

        entityTypeMetadata.put("elasticsearch_missing", entityIdsMissingInElasticsearch);
        entityTypeMetadata.put("elasticsearch_missing_count", entityIdsMissingInElasticsearch.size());
      }
    });

    return metadata;
  }

  @Override
  public void verifyIndex(EntityTags entityTags) {
    OperationPoller.retryWithBackoff(o -> {
        // verify that the indexed document can be retrieved (accounts for index lag)
        Map<String, Object> entityTagsCriteria = new HashMap<>();
        entityTags.getTags().stream().filter(entityTag -> entityTag != null && entityTag.getValueType() != null).forEach(entityTag -> {
          switch (entityTag.getValueType()) {
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

  @Override
  public Map reconcile(String cloudProvider, String account, String region, boolean dryRun) {
    return getElasticSearchEntityTagsReconciler().reconcile(this, cloudProvider, account, region, dryRun);
  }

  @Override
  public Map<String, Object> deleteByNamespace(String namespace, boolean dryRun, boolean deleteFromSource) {
    List<EntityTags> entityTagsForNamespace = getAllMatchingEntityTags(namespace, null);

    for (EntityTags entityTags : entityTagsForNamespace) {
      // ensure that all tags (and their metadata) in the offending namespace are removed
      entityTags.setTags(
        entityTags
          .getTags()
          .stream()
          .filter(e -> !namespace.equalsIgnoreCase(e.getNamespace()))
          .collect(Collectors.toList())
      );

      Set<String> tagNames = entityTags
        .getTags()
        .stream()
        .map(e -> e.getName().toLowerCase())
        .collect(Collectors.toSet());

      entityTags.setTagsMetadata(
        entityTags
          .getTagsMetadata()
          .stream()
          .filter(e -> tagNames.contains(e.getName().toLowerCase()))
          .collect(Collectors.toList())
      );
    }

    Map results = new HashMap() {{
      put("affectedIds", entityTagsForNamespace.stream().map(EntityTags::getId).collect(Collectors.toList()));
      put("deletedFromSource", false);
      put("deletedFromElasticsearch", false);
    }};

    if (!dryRun) {
      bulkIndex(entityTagsForNamespace);
      results.put("deletedFromElasticsearch", true);

      if (deleteFromSource) {
        Lists.partition(entityTagsForNamespace, 50).forEach(front50Service::batchUpdate);
        results.put("deletedFromSource", true);
      }
    }

    return results;
  }

  @Override
  public Map<String, Object> deleteByTag(String tag, boolean dryRun, boolean deleteFromSource) {
    List<EntityTags> entityTagsForTag = getAllMatchingEntityTags(null, tag);

    for (EntityTags entityTags : entityTagsForTag) {
      // ensure that all matching tags (and their metadata) are removed
      entityTags.setTags(
        entityTags
          .getTags()
          .stream()
          .filter(e -> !tag.equalsIgnoreCase(e.getName()))
          .collect(Collectors.toList())
      );

      Set<String> tagNames = entityTags
        .getTags()
        .stream()
        .map(e -> e.getName().toLowerCase())
        .collect(Collectors.toSet());

      entityTags.setTagsMetadata(
        entityTags
          .getTagsMetadata()
          .stream()
          .filter(e -> tagNames.contains(e.getName().toLowerCase()))
          .collect(Collectors.toList())
      );
    }

    Map results = new HashMap() {{
      put("affectedIds", entityTagsForTag.stream().map(EntityTags::getId).collect(Collectors.toList()));
      put("deletedFromSource", false);
      put("deletedFromElasticsearch", false);
    }};

    if (!dryRun) {
      bulkIndex(entityTagsForTag);
      results.put("deletedFromElasticsearch", true);

      if (deleteFromSource) {
        Lists.partition(entityTagsForTag, 50).forEach(front50Service::batchUpdate);
        results.put("deletedFromSource", true);
      }
    }

    return results;
  }

  private List<EntityTags> getAllMatchingEntityTags(String namespace, String tag) {
    Set<String> entityTagsIdentifiers = new HashSet<>();

    List<EntityTags> entityTagsForTag = front50Service
      .getAllEntityTags(false)
      .stream()
      .filter(e -> e.getTags().stream().anyMatch(t ->
        (namespace != null && namespace.equalsIgnoreCase(t.getNamespace())) || (tag != null && tag.equalsIgnoreCase(t.getName()))
      ))
      .collect(Collectors.toList());

    entityTagsIdentifiers.addAll(
      entityTagsForTag.stream().map(e -> e.getId().toLowerCase()).collect(Collectors.toSet())
    );

    if (tag != null) {
      BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery().must(
        applyTagsToBuilder(null, Collections.singletonMap(tag, "*"))
      );

      fetchAll(queryBuilder, null, 5000, "2m").forEach(entityTags -> {
        if (!entityTagsIdentifiers.contains(entityTags.getId())) {
          entityTagsForTag.add(entityTags);
          entityTagsIdentifiers.add(entityTags.getId().toLowerCase());
        }
      });
    }

    if (namespace != null) {
      BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery().must(
        applyTagsToBuilder(namespace, Collections.emptyMap())
      );

      fetchAll(queryBuilder, null, 5000, "2m").forEach(entityTags -> {
        if (!entityTagsIdentifiers.contains(entityTags.getId())) {
          entityTagsForTag.add(entityTags);
          entityTagsIdentifiers.add(entityTags.getId().toLowerCase());
        }
      });
    }

    return entityTagsForTag;
  }

  private QueryBuilder applyTagsToBuilder(String namespace, Map<String, Object> tags) {
    BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

    if (tags != null && !tags.isEmpty()) {
      for (Map.Entry<String, Object> entry : flatten(new HashMap<>(), null, tags).entrySet()) {
        // restrict to specific tags (optional)
        boolQueryBuilder.must(QueryBuilders.termQuery("tags.name", entry.getKey()));
        if (!entry.getValue().equals("*")) {
          boolQueryBuilder.must(QueryBuilders.matchQuery("tags.value", entry.getValue()));
        }
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
  private Map<String, Object> flatten(Map<String, Object> accumulator, String rootKey, Map<String, Object> criteria) {
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

  private List<EntityTags> fetchAll(QueryBuilder queryBuilder, String type, int scrollSize, String scrollTime) {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(queryBuilder);

    Search.Builder builder = new Search.Builder(searchSourceBuilder.toString())
      .addIndex(activeElasticSearchIndex);

    if (type != null) {
      builder.addType(type);
    }

    Search search = builder
      .setParameter(Parameters.SIZE, scrollSize)
      .setParameter(Parameters.SCROLL, scrollTime)
      .build();

    List<EntityTags> allEntityTags = new ArrayList<>();

    JestResult result;
    try {
      result = jestClient.execute(search);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    Collection<EntityTags> entityTags = result.getSourceAsObjectList(EntityTags.class);
    allEntityTags.addAll(entityTags);

    String scrollId = result.getJsonObject().get("_scroll_id").getAsString();

    try {
      while (entityTags.size() > 0) {
        SearchScroll scroll = new SearchScroll.Builder(scrollId, scrollTime)
          .setParameter(Parameters.SIZE, scrollSize).build();

        try {
          result = jestClient.execute(scroll);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }

        entityTags = result.getSourceAsObjectList(EntityTags.class);
        allEntityTags.addAll(entityTags);

        scrollId = result.getJsonObject().getAsJsonPrimitive("_scroll_id").getAsString();
      }

      return allEntityTags;
    } finally {
      try {
        jestClient.execute(new ClearScroll.Builder().addScrollId(scrollId).build());
      } catch (IOException e) {
        log.warn("Unable to clear scroll id {}", scrollId, e);
      }
    }
  }

  private ElasticSearchEntityTagsReconciler getElasticSearchEntityTagsReconciler() {
    return applicationContext.getBean(ElasticSearchEntityTagsReconciler.class);
  }

  private static EntityTags prepareForWrite(ObjectMapper objectMapper, EntityTags entityTags) {
    EntityTags copyOfEntityTags = objectMapper.convertValue(
      objectMapper.convertValue(entityTags, Map.class), EntityTags.class
    );

    copyOfEntityTags.getTags().forEach(entityTag -> entityTag.setValue(entityTag.getValueForWrite(objectMapper)));

    String application = copyOfEntityTags.getEntityRef().getApplication();
    if (application == null || application.trim().isEmpty()) {
      try {
        Names names = Names.parseName(copyOfEntityTags.getEntityRef().getEntityId());
        copyOfEntityTags.getEntityRef().setApplication(names.getApp());
      } catch (Exception e) {
        log.error(
          "Unable to extract application name (entityId: {})",
          copyOfEntityTags.getEntityRef().getEntityId(),
          e
        );
      }
    }

    return copyOfEntityTags;
  }

  private static EntityTags prepareForRead(ObjectMapper objectMapper, Map indexedEntityTags) {
    EntityTags entityTags = objectMapper.convertValue(indexedEntityTags, EntityTags.class);
    entityTags.getTags().forEach(entityTag -> entityTag.setValue(entityTag.getValueForRead(objectMapper)));

    return entityTags;
  }

  private String getDocumentType(EntityTags entityTags) {
    if (singleMappingType) {
      return mappingTypeName;
    } else {
      return entityTags.getEntityRef().getEntityType();
    }
  }
}

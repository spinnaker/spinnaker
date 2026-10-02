/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.netflix.spinnaker.clouddriver.entitytags.migration;

import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.model.EntityTags;
import com.netflix.spinnaker.clouddriver.model.EntityTagsProvider;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Opt-in (entity-tags.front50-migration.enabled=true) one-shot migration: on startup, copies every EntityTags record from Front50 into the
 * clouddriver-side EntityTagsProvider. Skips itself if the provider already has any records, so
 * repeated deploys are idempotent. Remove this class (and Front50Service's read endpoints) in a
 * follow-up once every environment has run through migration.
 */
@Component
@ConditionalOnBean({Front50Service.class, EntityTagsProvider.class})
@ConditionalOnProperty(value = "entity-tags.front50-migration.enabled")
public class Front50ToClouddriverEntityTagsMigrator {

  private static final Logger log =
      LoggerFactory.getLogger(Front50ToClouddriverEntityTagsMigrator.class);

  private final Front50Service front50Service;
  private final EntityTagsProvider entityTagsProvider;
  private final int batchSize;

  public Front50ToClouddriverEntityTagsMigrator(
      Front50Service front50Service,
      EntityTagsProvider entityTagsProvider,
      @Value("${entity-tags.front50-migration.batch-size:200}") int batchSize) {
    this.front50Service = front50Service;
    this.entityTagsProvider = entityTagsProvider;
    this.batchSize = batchSize;
  }

  @PostConstruct
  public void migrate() {
    Collection<EntityTags> existing =
        entityTagsProvider.getAll(null, null, null, null, null, null, null, null, null, 1);
    if (!existing.isEmpty()) {
      log.info("Skipping Front50→SQL entity tags migration; provider already has records");
      return;
    }

    Collection<EntityTags> tags;
    try {
      tags = Retrofit2SyncCall.execute(front50Service.getAllEntityTags(false));
    } catch (Exception e) {
      log.warn("Unable to fetch entity tags from Front50; skipping migration", e);
      return;
    }

    if (tags == null || tags.isEmpty()) {
      log.info("Front50 returned no entity tags; nothing to migrate");
      return;
    }

    log.info("Migrating {} entity tag(s) from Front50 into clouddriver", tags.size());
    List<EntityTags> batch = new ArrayList<>(batchSize);
    int migrated = 0;
    for (EntityTags tag : tags) {
      batch.add(tag);
      if (batch.size() >= batchSize) {
        entityTagsProvider.bulkIndex(batch);
        migrated += batch.size();
        batch.clear();
      }
    }
    if (!batch.isEmpty()) {
      entityTagsProvider.bulkIndex(batch);
      migrated += batch.size();
    }
    log.info("Front50→clouddriver entity tag migration complete: {} record(s)", migrated);
  }
}

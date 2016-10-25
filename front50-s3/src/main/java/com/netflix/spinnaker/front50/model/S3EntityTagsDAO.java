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

package com.netflix.spinnaker.front50.model;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.front50.model.tag.EntityTags;
import com.netflix.spinnaker.front50.model.tag.EntityTagsDAO;
import rx.Scheduler;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class S3EntityTagsDAO extends S3Support<EntityTags> implements EntityTagsDAO {
  private final AmazonS3 amazonS3;
  private final String bucket;

  public S3EntityTagsDAO(ObjectMapper objectMapper,
                         AmazonS3 amazonS3,
                         Scheduler scheduler,
                         int refreshIntervalMs,
                         String bucket,
                         String rootFolder) {
    super(objectMapper, amazonS3, scheduler, refreshIntervalMs, bucket, (rootFolder + "/tags/").replaceAll("//", "/"));

    this.amazonS3 = amazonS3;
    this.bucket = bucket;
  }

  @Override
  public Set<EntityTags> all(String prefix) {
    ObjectListing bucketListing = amazonS3.listObjects(
      new ListObjectsRequest(bucket, (rootFolder + prefix).toLowerCase(), null, null, 100)
    );
    List<S3ObjectSummary> summaries = bucketListing.getObjectSummaries();

    // TODO-AJ this is naive and inefficient
    return summaries.stream()
      .map(s3ObjectSummary -> amazonS3.getObject(s3ObjectSummary.getBucketName(), s3ObjectSummary.getKey()))
      .map(s3Object -> {
        EntityTags item = null;
        try {
          item = deserialize(s3Object);
          item.setLastModified(s3Object.getObjectMetadata().getLastModified().getTime());
        } catch (IOException e) {
          // do nothing
        }
        return item;
      })
      .collect(Collectors.toSet());
  }

  @Override
  public EntityTags create(String id, EntityTags tag) {
    Objects.requireNonNull(id);
    return upsert(id, tag);
  }

  @Override
  public void update(String id, EntityTags tag) {
    Objects.requireNonNull(id);
    upsert(id, tag);
  }

  private EntityTags upsert(String id, EntityTags tag) {
    super.update(id, tag);
    return findById(id);
  }

  @Override
  public Collection<EntityTags> all() {
    // no support for retrieving _all_ tagged entities
    return Collections.emptySet();
  }

  @Override
  protected void refresh() {
    // avoid loading all tagged entities into memory
  }

  @Override
  protected void writeLastModified() {
    // avoid writing `last-modified.json` for every update
  }

  @Override
  Class<EntityTags> getSerializedClass() {
    return EntityTags.class;
  }

  @Override
  String getMetadataFilename() {
    return "tagged-entity-metadata.json";
  }
}


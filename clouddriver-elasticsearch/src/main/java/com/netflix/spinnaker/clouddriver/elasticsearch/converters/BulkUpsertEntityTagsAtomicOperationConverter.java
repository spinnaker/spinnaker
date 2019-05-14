/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.elasticsearch.converters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.BulkUpsertEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.elasticsearch.ops.BulkUpsertEntityTagsAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.kork.core.RetrySupport;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("bulkUpsertEntityTagsDescription")
public class BulkUpsertEntityTagsAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  private final ObjectMapper objectMapper;
  private final RetrySupport retrySupport;
  private final Front50Service front50Service;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;

  @Autowired
  public BulkUpsertEntityTagsAtomicOperationConverter(
      ObjectMapper objectMapper,
      RetrySupport retrySupport,
      Front50Service front50Service,
      AccountCredentialsProvider accountCredentialsProvider,
      ElasticSearchEntityTagsProvider entityTagsProvider) {
    this.objectMapper = objectMapper;
    this.retrySupport = retrySupport;
    this.front50Service = front50Service;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.entityTagsProvider = entityTagsProvider;
  }

  public AtomicOperation convertOperation(Map input) {
    return new BulkUpsertEntityTagsAtomicOperation(
        retrySupport,
        front50Service,
        accountCredentialsProvider,
        entityTagsProvider,
        this.convertDescription(input));
  }

  public BulkUpsertEntityTagsDescription convertDescription(Map input) {
    BulkUpsertEntityTagsDescription description =
        objectMapper.convertValue(input, BulkUpsertEntityTagsDescription.class);
    description.entityTags.forEach(
        upsertEntityTagsDescription ->
            upsertEntityTagsDescription
                .getTags()
                .forEach(UpsertEntityTagsAtomicOperationConverter::setTagValueType));
    return description;
  }
}

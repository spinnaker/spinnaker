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

package com.netflix.spinnaker.clouddriver.elasticsearch.ops;

import com.google.common.collect.Lists;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.BulkUpsertEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;

import java.util.List;

public class BulkUpsertEntityTagsAtomicOperation implements AtomicOperation<Void> {

  private final Front50Service front50Service;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;
  private final BulkUpsertEntityTagsDescription bulkUpsertEntityTagsDescription;

  public BulkUpsertEntityTagsAtomicOperation(Front50Service front50Service,
                                             AccountCredentialsProvider accountCredentialsProvider,
                                             ElasticSearchEntityTagsProvider entityTagsProvider,
                                             BulkUpsertEntityTagsDescription bulkUpsertEntityTagsDescription) {
    this.front50Service = front50Service;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.entityTagsProvider = entityTagsProvider;
    this.bulkUpsertEntityTagsDescription = bulkUpsertEntityTagsDescription;
  }

  public Void operate(List priorOutputs) {
    bulkUpsertEntityTagsDescription.entityTags.forEach(tag ->
      new UpsertEntityTagsAtomicOperation(front50Service, accountCredentialsProvider, entityTagsProvider, tag)
        .operate(priorOutputs)
    );
    return null;
  }
}

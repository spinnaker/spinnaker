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

package com.netflix.spinnaker.clouddriver.elasticsearch.converters;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.clouddriver.elasticsearch.descriptions.DeleteEntityTagsDescription;
import com.netflix.spinnaker.clouddriver.elasticsearch.model.ElasticSearchEntityTagsProvider;
import com.netflix.spinnaker.clouddriver.elasticsearch.ops.DeleteEntityTagsAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("deleteEntityTags")
public class DeleteEntityTagsAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {
  private final ObjectMapper objectMapper;
  private final Front50Service front50Service;
  private final ElasticSearchEntityTagsProvider entityTagsProvider;

  @Autowired
  public DeleteEntityTagsAtomicOperationConverter(
      ObjectMapper objectMapper,
      Front50Service front50Service,
      ElasticSearchEntityTagsProvider entityTagsProvider) {
    this.objectMapper =
        objectMapper
            .copy()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    this.front50Service = front50Service;
    this.entityTagsProvider = entityTagsProvider;
  }

  public AtomicOperation convertOperation(Map input) {
    return buildOperation(convertDescription(input));
  }

  public AtomicOperation buildOperation(DeleteEntityTagsDescription description) {
    return new DeleteEntityTagsAtomicOperation(front50Service, entityTagsProvider, description);
  }

  public DeleteEntityTagsDescription convertDescription(Map input) {
    return objectMapper.convertValue(input, DeleteEntityTagsDescription.class);
  }
}

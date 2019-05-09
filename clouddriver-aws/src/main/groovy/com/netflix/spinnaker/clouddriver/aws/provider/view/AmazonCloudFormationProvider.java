/*
 * Copyright (c) 2019 Schibsted Media Group.
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

package com.netflix.spinnaker.clouddriver.aws.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.aws.cache.Keys;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonCloudFormationStack;
import com.netflix.spinnaker.clouddriver.aws.model.CloudFormationProvider;
import com.netflix.spinnaker.clouddriver.aws.model.CloudFormationStack;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.STACKS;

@Slf4j
@Component
public class AmazonCloudFormationProvider implements CloudFormationProvider<CloudFormationStack> {

  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  @Autowired
  public AmazonCloudFormationProvider(Cache cacheView, @Qualifier("amazonObjectMapper") ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  public List<CloudFormationStack> list(String accountName, String region) {
    String filter = Keys.getCloudFormationKey("*", region, accountName);
    log.debug("List all stacks with filter {}", filter);
    return loadResults(cacheView.filterIdentifiers(STACKS.getNs(), filter));
  }

  @Override
  public Optional<CloudFormationStack> get(String stackId) {
    String filter = Keys.getCloudFormationKey(stackId, "*", "*");
    log.debug("Get stack with filter {}", filter);
    return loadResults(cacheView.filterIdentifiers(STACKS.getNs(), filter)).stream().findFirst();
  }

  List<CloudFormationStack> loadResults(Collection<String> identifiers) {
    return cacheView.getAll(STACKS.getNs(), identifiers, RelationshipCacheFilter.none())
      .stream()
      .map(data -> {
        log.debug("Cloud formation cached properties {}", data.getAttributes());
        return objectMapper.convertValue(data.getAttributes(), AmazonCloudFormationStack.class);
      })
      .collect(Collectors.toList());
  }
}

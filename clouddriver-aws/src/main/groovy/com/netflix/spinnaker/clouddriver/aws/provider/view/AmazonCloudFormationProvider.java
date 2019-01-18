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
import com.netflix.spinnaker.clouddriver.aws.model.AmazonCloudFormation;
import com.netflix.spinnaker.clouddriver.model.CloudFormationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.aws.cache.Keys.Namespace.CLOUDFORMATION;

@Slf4j
@Component
class AmazonCloudFormationProvider implements CloudFormationProvider<AmazonCloudFormation> {

  private final Cache cacheView;
  private final ObjectMapper objectMapper;

  @Autowired
  AmazonCloudFormationProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView;
    this.objectMapper = objectMapper;
  }

  public List<AmazonCloudFormation> list(String account, String region) {
    String filter = Keys.getCloudFormationKey("*", region, account);
    log.debug("List all stacks with filter {}", filter);
    return loadResults(cacheView.filterIdentifiers(CLOUDFORMATION.getNs(), filter));
  }

  @Override
  public Optional<AmazonCloudFormation> get(String stackId) {
    String filter = Keys.getCloudFormationKey(stackId, "*", "*");
    log.debug("Get stack with filter {}", filter);
    return loadResults(cacheView.filterIdentifiers(CLOUDFORMATION.getNs(), filter)).stream().findFirst();
  }

  List<AmazonCloudFormation> loadResults(Collection<String> identifiers) {
    return cacheView.getAll(CLOUDFORMATION.getNs(), identifiers, RelationshipCacheFilter.none())
      .stream()
      .map(data -> {
        log.debug("Cloud formation cached properties {}", data.getAttributes());
        return objectMapper.convertValue(data.getAttributes(), AmazonCloudFormation.class);
      })
      .collect(Collectors.toList());
  }
}

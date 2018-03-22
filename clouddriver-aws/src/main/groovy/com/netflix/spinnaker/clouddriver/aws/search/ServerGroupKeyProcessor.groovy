/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.search

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.cache.KeyProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.SERVER_GROUPS

@Component("AmazonServerGroupKeyProcessor")
class ServerGroupKeyProcessor implements KeyProcessor {

  @Autowired
  private final Cache cacheView

  @Override
  Boolean canProcess(String type) {
    return type == "serverGroups"
  }

  @Override
  Boolean exists(String serverGroupKey) {
    return cacheView.get(SERVER_GROUPS.ns, serverGroupKey, RelationshipCacheFilter.none()) != null
  }
}

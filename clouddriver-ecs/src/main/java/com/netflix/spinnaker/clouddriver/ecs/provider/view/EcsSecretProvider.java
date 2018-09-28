/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.SecretCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Secret;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public class EcsSecretProvider {

  private SecretCacheClient secretCacheClient;

  @Autowired
  public EcsSecretProvider(Cache cacheView) {
    this.secretCacheClient = new SecretCacheClient(cacheView);
  }

  public Collection<Secret> getAllSecrets() {
    return secretCacheClient.getAll();
  }

}

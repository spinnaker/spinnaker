/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Slf4j
public class KubernetesV2CachingAgentDispatcher implements KubernetesCachingAgentDispatcher {
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private Registry registry;

  @Autowired
  private KubernetesResourcePropertyRegistry propertyRegistry;

  @Override
  public List<KubernetesCachingAgent> buildAllCachingAgents(KubernetesNamedAccountCredentials credentials) {
    return IntStream.range(0, credentials.getCacheThreads())
        .boxed()
        .map(i -> propertyRegistry.values()
            .stream()
            .map(KubernetesResourceProperties::getHandler)
            .map(KubernetesHandler::cachingAgentClass)
            .filter(Objects::nonNull)
            .map(c -> {
                  try {
                    return c.getDeclaredConstructor(
                        KubernetesNamedAccountCredentials.class,
                        ObjectMapper.class,
                        Registry.class,
                        int.class,
                        int.class
                    );
                  } catch (NoSuchMethodException e) {
                    log.warn("Missing canonical constructor", e);
                    return null;
                  }
                }
            )
            .filter(Objects::nonNull)
            .map(c -> {
                  try {
                    return (KubernetesV2CachingAgent) c.newInstance(
                        credentials,
                        objectMapper,
                        registry,
                        i,
                        credentials.getCacheThreads()
                    );
                  } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    log.warn("Can't invoke caching agent constructor", e);
                    return null;
                  }
                }
            )
            .filter(Objects::nonNull)
        )
        .flatMap(s -> s)
        .collect(Collectors.toList());
  }
}

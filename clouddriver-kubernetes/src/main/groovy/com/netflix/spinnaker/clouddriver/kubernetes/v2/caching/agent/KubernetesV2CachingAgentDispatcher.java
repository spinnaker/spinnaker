package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgent;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesCachingAgentDispatcher;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class KubernetesV2CachingAgentDispatcher implements KubernetesCachingAgentDispatcher {
  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private Registry registry;

  @Override
  public List<KubernetesCachingAgent> buildAllCachingAgents(KubernetesNamedAccountCredentials credentials) {
    return IntStream.range(0, credentials.getCacheThreads())
        .boxed()
        .map(i -> new ArrayList<KubernetesCachingAgent>(Arrays.asList(
            new KubernetesReplicaSetCachingAgent(credentials, objectMapper, registry, i, credentials.getCacheThreads())
        )))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }
}

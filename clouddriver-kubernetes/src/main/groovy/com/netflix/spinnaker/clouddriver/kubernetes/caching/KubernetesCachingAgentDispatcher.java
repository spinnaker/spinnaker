package com.netflix.spinnaker.clouddriver.kubernetes.caching;

import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent.KubernetesCachingAgent;

import java.util.List;

public interface KubernetesCachingAgentDispatcher {
  List<KubernetesCachingAgent> buildAllCachingAgents(KubernetesNamedAccountCredentials credentials);
}

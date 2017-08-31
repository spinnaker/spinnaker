package com.netflix.spinnaker.clouddriver.kubernetes.caching;

import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials;

import java.util.List;

public interface KubernetesCachingAgentDispatcher {
  List<KubernetesCachingAgent> buildAllCachingAgents(KubernetesNamedAccountCredentials credentials);
}

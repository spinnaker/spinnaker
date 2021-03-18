package com.netflix.spinnaker.clouddriver.core.provider.agent;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.INSTANCES;

import com.fasterxml.jackson.core.type.TypeReference;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import java.util.*;

public interface HealthProvidingCachingAgent extends CachingAgent {
  TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {};

  Collection<AgentDataType> types =
      Collections.unmodifiableCollection(
          new ArrayList<>(
              Arrays.asList(
                  AUTHORITATIVE.forType(HEALTH.getNs()), INFORMATIVE.forType(INSTANCES.getNs()))));

  String getHealthId();
}

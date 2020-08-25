package com.netflix.spinnaker.clouddriver.core.provider;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.provider.Provider;
import java.util.Collection;

public class CoreProvider extends AgentSchedulerAware implements Provider {

  public static final String PROVIDER_NAME = CoreProvider.class.getName();

  private final Collection<Agent> agents;

  public CoreProvider(Collection<Agent> agents) {
    this.agents = agents;
  }

  @Override
  public String getProviderName() {
    return PROVIDER_NAME;
  }

  @Override
  public Collection<Agent> getAgents() {
    return agents;
  }
}

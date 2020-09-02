package com.netflix.spinnaker.orca

import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder
import org.springframework.beans.BeansException
import org.springframework.beans.factory.ObjectProvider

class StageDefinitionBuildersProvider implements ObjectProvider<Collection<StageDefinitionBuilder>> {

  Collection<StageDefinitionBuilder> builders

  StageDefinitionBuildersProvider(Collection<StageDefinitionBuilder> builders) {
    this.builders = builders
  }

  @Override
  Collection<StageDefinitionBuilder> getObject(Object... args) throws BeansException {
    return builders
  }

  @Override
  Collection<StageDefinitionBuilder> getIfAvailable() throws BeansException {
    return builders
  }

  @Override
  Collection<StageDefinitionBuilder> getIfUnique() throws BeansException {
    return builders
  }

  @Override
  Collection<StageDefinitionBuilder> getObject() throws BeansException {
    return builders
  }
}

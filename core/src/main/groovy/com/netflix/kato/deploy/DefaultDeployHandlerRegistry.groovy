package com.netflix.kato.deploy

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DefaultDeployHandlerRegistry implements DeployHandlerRegistry {

  @Autowired
  List<DeployHandler> deployHandlers

  @Override
  DeployHandler findHandler(DeployDescription description) {
    def handler = deployHandlers.find { it.handles(description) }
    if (!handler) {
      throw new DeployHandlerNotFoundException()
    } else {
      handler
    }
  }
}

package com.netflix.kato.deploy

import groovy.transform.InheritConstructors
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DeploymentHandlerRegistry {

  @Autowired
  List<DeployHandler> deployHandlers

  DeployHandler findHandler(DeployDescription description) {
    def handler = deployHandlers.find { it.handles(description) }
    if (!handler) {
      throw new DeployHandlerNotFoundException()
    } else {
      handler
    }
  }

  @InheritConstructors
  static class DeployHandlerNotFoundException extends RuntimeException {}
}

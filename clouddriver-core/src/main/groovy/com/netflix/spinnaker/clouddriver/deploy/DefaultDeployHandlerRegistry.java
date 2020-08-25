package com.netflix.spinnaker.clouddriver.deploy;

import static java.lang.String.format;

import java.util.List;

public class DefaultDeployHandlerRegistry implements DeployHandlerRegistry {

  private List<DeployHandler> deployHandlers;

  public DefaultDeployHandlerRegistry(List<DeployHandler> deployHandlers) {
    this.deployHandlers = deployHandlers;
  }

  @Override
  public DeployHandler findHandler(final DeployDescription description) {
    return deployHandlers.stream()
        .filter(it -> it.handles(description))
        .findFirst()
        .orElseThrow(
            () ->
                new DeployHandlerNotFoundException(
                    format(
                        "No handler found supportign %s", description.getClass().getSimpleName())));
  }

  public List<DeployHandler> getDeployHandlers() {
    return deployHandlers;
  }

  public void setDeployHandlers(List<DeployHandler> deployHandlers) {
    this.deployHandlers = deployHandlers;
  }
}

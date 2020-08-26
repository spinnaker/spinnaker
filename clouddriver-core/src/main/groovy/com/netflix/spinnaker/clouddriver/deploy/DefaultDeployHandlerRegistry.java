package com.netflix.spinnaker.clouddriver.deploy;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DefaultDeployHandlerRegistry implements DeployHandlerRegistry {

  private List<DeployHandler> deployHandlers;

  public DefaultDeployHandlerRegistry(List<DeployHandler> deployHandlers) {
    this.deployHandlers = deployHandlers;
  }

  @Override
  public DeployHandler findHandler(final DeployDescription description) {
    return Optional.ofNullable(deployHandlers).orElseGet(ArrayList::new).stream()
        .filter(it -> it != null && it.handles(description))
        .findFirst()
        .orElseThrow(
            () ->
                new DeployHandlerNotFoundException(
                    format(
                        "No handler found supporting %s", description.getClass().getSimpleName())));
  }
}

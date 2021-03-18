package com.netflix.spinnaker.clouddriver.deploy;

/** A registry of {@link DeployHandler} instances. */
public interface DeployHandlerRegistry {
  /**
   * This method is used to locate a handler most appropriate for the provided description object.
   *
   * @param description
   * @return a deploy handler instance
   * @throws DeployHandlerNotFoundException
   */
  DeployHandler findHandler(DeployDescription description) throws DeployHandlerNotFoundException;
}

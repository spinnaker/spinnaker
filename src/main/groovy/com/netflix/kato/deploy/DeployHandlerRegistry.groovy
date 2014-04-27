package com.netflix.kato.deploy

/**
 * A registry of {@link DeployHandler} instances.
 *
 * @author Dan Woods
 */
public interface DeployHandlerRegistry {
  /**
   * This method is used to locate a handler most appropriate for the provided description object.
   *
   * @param description
   * @return a deploy handler instance
   * @throws DeployHandlerNotFoundException
   */
  DeployHandler findHandler(DeployDescription description) throws DeployHandlerNotFoundException
}
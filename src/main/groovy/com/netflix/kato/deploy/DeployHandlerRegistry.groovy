package com.netflix.kato.deploy

public interface DeployHandlerRegistry {
  DeployHandler findHandler(DeployDescription description) throws DeployHandlerNotFoundException
}
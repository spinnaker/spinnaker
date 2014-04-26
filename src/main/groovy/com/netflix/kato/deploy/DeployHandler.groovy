package com.netflix.kato.deploy

public interface DeployHandler<T> {
  DeploymentResult handle(T description)
  boolean handles(DeployDescription description)
}

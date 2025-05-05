package com.netflix.spinnaker.kork.docker.service;

import okhttp3.OkHttpClient;

/** Allows custom configuration of the Docker Registry OkHttpClient. */
public interface DockerOkClientProvider {

  /**
   * @param address Provided simply in case a client provider needs to conditionally apply rules
   *     per-registry
   * @param timeoutMs The client timeout in milliseconds
   * @param insecure Whether or not the registry should be configured to trust all SSL certificates.
   *     If this is true, you may want to fallback to {@code DefaultDockerOkClientProvider}
   * @return OkHttpClient
   */
  OkHttpClient provide(String address, long timeoutMs, boolean insecure);
}

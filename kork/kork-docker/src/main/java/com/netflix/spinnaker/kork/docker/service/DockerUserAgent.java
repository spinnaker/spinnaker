package com.netflix.spinnaker.kork.docker.service;

public class DockerUserAgent {
  public static String getUserAgent() {
    String version;
    try {
      version = DockerUserAgent.class.getPackage().getImplementationVersion();
    } catch (Exception _ignored) {
      version = "Unknown";
    }

    return "Spinnaker/" + version;
  }
}

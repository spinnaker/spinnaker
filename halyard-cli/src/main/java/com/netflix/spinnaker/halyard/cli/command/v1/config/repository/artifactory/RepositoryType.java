package com.netflix.spinnaker.halyard.cli.command.v1.config.repository.artifactory;

import lombok.Getter;

public enum RepositoryType {
  MAVEN("maven"),
  HELM("helm");

  @Getter private final String type;

  RepositoryType(String type) {
    this.type = type;
  }
}

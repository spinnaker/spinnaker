package com.netflix.spinnaker.kork.secrets;

import javax.annotation.Nonnull;
import lombok.Getter;

public enum StandardSecretParameter {
  KEY("k"),
  ENCODING("e");

  @Getter @Nonnull private final String parameterName;

  StandardSecretParameter(String parameterName) {
    this.parameterName = parameterName;
  }
}

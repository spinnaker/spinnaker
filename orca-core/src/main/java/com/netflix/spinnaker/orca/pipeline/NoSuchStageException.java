package com.netflix.spinnaker.orca.pipeline;

import static java.lang.String.format;

public class NoSuchStageException extends RuntimeException {
  public NoSuchStageException(String stageType) {
    super(format("Unknown stage '%s' requested.", stageType));
  }
}

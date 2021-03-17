package com.netflix.spinnaker.orca.api.operations;

import javax.annotation.Nonnull;

/** An operations runner will run one or more operation and return the resulting context. */
public interface OperationsRunner {

  /** Run one or more operations. */
  OperationsContext run(@Nonnull OperationsInput operationsInput);
}

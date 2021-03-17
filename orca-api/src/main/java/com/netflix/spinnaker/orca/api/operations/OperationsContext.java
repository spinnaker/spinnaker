package com.netflix.spinnaker.orca.api.operations;

import javax.annotation.Nonnull;

/**
 * A placeholder for the result of {@link OperationsRunner#run} invocations.
 *
 * <p>For example, this may be a completed operation, or perhaps it's the ID of a running operation
 * - this would depend on the underlying operation that was invoked via the {@link OperationsRunner}
 * implementation.
 */
public interface OperationsContext {

  /**
   * They operation context key. This may be used to identify the operations context value at a
   * later point in time.
   */
  @Nonnull
  String contextKey();

  /**
   * The value of the operations context. May be an ID if the system is tracking the operations
   * execution asynchronously, or it may be the result value of the operation.
   */
  @Nonnull
  OperationsContextValue contextValue();

  /** Marker interface for {@link OperationsContext#contextValue()} return value. */
  interface OperationsContextValue {}
}

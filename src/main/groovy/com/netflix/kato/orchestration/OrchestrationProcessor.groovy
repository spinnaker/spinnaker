package com.netflix.kato.orchestration

/**
 * Implementations of this interface should perform orchestration of operations in a workflow. Often will be used in
 * conjunction with {@link AtomicOperation} instances.
 *
 * @author Dan Woods
 */
public interface OrchestrationProcessor {
  /**
   * This is the invocation point of orchestration.
   *
   * @return a list of results
   */
  List process()
}
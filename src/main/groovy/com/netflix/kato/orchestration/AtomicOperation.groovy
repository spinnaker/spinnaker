package com.netflix.kato.orchestration

/**
 * An AtomicOperation is the most fundamental, low-level unit of work in a workflow. Implementations of this interface
 * should perform the simplest form of work possible, often described by a description object (like {@link com.netflix.kato.deploy.DeployDescription}
 *
 * @param the return type of the operation
 * @author Dan Woods
 */
public interface AtomicOperation<R> {
  /**
   * This method will initiate the operation's work. In this, operation's can get a handle on prior output results
   * from the requiremed method argument.
   *
   * @param priorOutputs
   * @return parameterized type
   */
  R operate(List priorOutputs)
}

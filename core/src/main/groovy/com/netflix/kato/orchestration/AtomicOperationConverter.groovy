package com.netflix.kato.orchestration

/**
 * Implementations of this interface will provide an object capable of converting a Map of input parameters to an
 * operation's description object and an {@link AtomicOperation} instance.
 *
 * @author Dan Woods
 */
interface AtomicOperationConverter {
  /**
   * This method takes a Map input and converts it to an {@link AtomicOperation} instance.
   *
   * @param input
   * @return atomic operation
   */
  AtomicOperation convertOperation(Map input)

  /**
   * This method takes a Map input and creates a description object, that will often be used by an {@link AtomicOperation}.
   *
   * @param input
   * @return instance of an operation description object
   */
  Object convertDescription(Map input)
}
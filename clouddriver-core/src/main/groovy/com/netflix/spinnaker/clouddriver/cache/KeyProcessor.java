package com.netflix.spinnaker.clouddriver.cache;

public interface KeyProcessor {

  /**
   * Indicates whether this processor can process the specified type
   *
   * @param type the cache type to process
   *
   * @return <code>true</code> if this processor can process the specified type and <code>false</code> otherwise.
   */
  Boolean canProcess(String type);

  /**
   * Determines whether the underlying object represented by this key exists.
   *
   * @param key the cache key to process
   *
   * @return <code>true</code> if the underlying object represented by this key exists and <code>false</code> otherwise.
   */
  Boolean exists(String key);
}

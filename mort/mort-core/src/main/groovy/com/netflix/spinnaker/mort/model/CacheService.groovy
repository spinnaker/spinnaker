/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */



package com.netflix.spinnaker.mort.model

/**
 * An interface for exposing a caching service API
 *
 * @author Dan Woods
 */
public interface CacheService {

  /**
   * Extracts an object from cache
   *
   * @param key
   * @return object
   */
  public <T> T retrieve(String key, Class<T> type)

  /**
   * Stores a keyed value in cache. Returns a true/false depending on whether the insertion succeeded or failed
   *
   * @param key
   * @param object
   */
  boolean put(String key, Object object)

  /**
   * Removes a keyed value from cache
   *
   * @param key
   */
  void free(String key)

  /**
   * Checks whether a value associated with a specified key exists
   *
   * @param key
   * @return true/false
   */
  boolean exists(String key)

  /**
   * Retrieve the keys from the cache
   *
   * @return set of keys
   */
  Set<String> keys()

  /**
   * Retrieves a subset of keys by the key namespace
   */
  Set<String> keysByType(String type)

  /**
   * Retrieves a subset of keys by the key namespace.
   *
   * Converts the type to string to determine namespace.
   */
  Set<String> keysByType(Object type)

}

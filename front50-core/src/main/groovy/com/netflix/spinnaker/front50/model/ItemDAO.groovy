/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.front50.model

import com.netflix.spinnaker.front50.exception.NotFoundException

interface ItemDAO<T> {
  T findById(String id) throws NotFoundException
  Collection<T> all()

  /**
   * It can be expensive to refresh a bucket containing a large number of objects.
   *
   * When {@code refresh} is false, the most recently cached set of objects will be returned.
   */
  Collection<T> all(boolean refresh)

  Collection<T> history(String id, int maxResults)

  T create(String id, T item)
  void update(String id, T item)
  void delete(String id)


  void bulkImport(Collection<T> items)

  boolean isHealthy()
}

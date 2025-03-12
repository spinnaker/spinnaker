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

package com.netflix.spinnaker.cats.mem

import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.WriteableCacheSpec

class InMemoryCacheSpec extends WriteableCacheSpec {

    @Override
    InMemoryCache getSubject() {
        new InMemoryCache()
    }

  def 'mergeAll with two items that have the same id uses the second item'() {
      given: 'one item in the cache'
      String id = 'bar'
      def itemOneAttributes = [att1: 'val1']
      CacheData itemOne = createData(id, itemOneAttributes)
      def itemTwoAttributes = [att2: 'val2']
      CacheData itemTwo = createData(id, itemTwoAttributes)
      String type = 'foo'
      cache.mergeAll(type, [ itemOne ])
      assert itemOneAttributes.equals(cache.get(type, id).attributes)

      when: 'adding both items'
      cache.mergeAll(type, [ itemOne, itemTwo ])

      then: 'itemTwo is in the cache'
      itemTwoAttributes.equals(cache.get(type, id).attributes)

      when: 'storing the items again'
      cache.mergeAll(type, [ itemOne, itemTwo ])

      then: 'itemTwo is still in the cache'
      itemTwoAttributes.equals(cache.get(type, id).attributes)
  }
}

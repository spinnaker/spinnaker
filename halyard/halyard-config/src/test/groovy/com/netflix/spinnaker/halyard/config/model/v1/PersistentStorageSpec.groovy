/*
 * Copyright 2017 Microsoft, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.model.v1

import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStorage
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.AzsPersistentStore
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.GcsPersistentStore
import com.netflix.spinnaker.halyard.config.model.v1.persistentStorage.S3PersistentStore
import spock.lang.Specification

class PersistentStorageSpec extends Specification {
  void "persistentStorage correctly reports configurable persistentStores"() {
    setup:
    def persistentStorage = new PersistentStorage()
    def iterator = persistentStorage.getChildren()
    def s3 = false
    def gcs = false
    def azs = false

    when:
    def child = iterator.getNext()
    while (child != null) {
      if (child instanceof GcsPersistentStore) {
        gcs = true
      }

      if (child instanceof S3PersistentStore) {
        s3 = true
      }

      if (child instanceof AzsPersistentStore) {
        azs = true
      }

      child = iterator.getNext()
    }

    then:
    gcs
    s3
    azs
  }
}

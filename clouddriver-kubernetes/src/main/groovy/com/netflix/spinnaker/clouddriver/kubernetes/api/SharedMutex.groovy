/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.api

import java.util.concurrent.Semaphore

/*
 * TODO(lwander): Delete once https://github.com/fabric8io/kubernetes-client/issues/408 is resolved
 *
 * To ensure that once a k8s client has been created its config isn't overwritten by another thread, we briefly lock
 * access to the client with this mutex.
 *
 * Note, this only needs to happen when accessing the `extensions` API (see above issue for details).
 */
class SharedMutex {
  static Semaphore sem

  static void lock() {
    if (sem == null) {
      sem = new Semaphore(1)
    }

    sem.acquire()
  }

  static void unlock() {
    if (sem == null) {
      throw new IllegalStateException("Attempt made to unlock mutex that was never locked")
    }

    sem.release()
  }
}

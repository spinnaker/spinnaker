/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy

import java.util.concurrent.Semaphore

class AppengineMutexRepository {
  static HashMap<String, Mutex> mutexRepository = new HashMap<>()

  static <T> T atomicWrapper(String mutexKey, Closure<T> doOperation) {
    if (!mutexRepository.containsKey(mutexKey)) {
      mutexRepository.put(mutexKey, new Mutex())
    }
    Mutex mutex = mutexRepository.get(mutexKey)

    // Outside the try {} block, because in the case of an exception being thrown here, we don't want to try unlocking
    // the mutex in the finally block.
    mutex.lock()
    T result = null
    Exception failure
    try {
      result = doOperation()
    } catch (Exception e) {
      failure = e
    } finally {
      mutex.unlock()
      if (failure) {
        throw failure
      } else {
        return result
      }
    }
  }

  static class Mutex {
    Semaphore sem

    void lock() {
      if (sem == null) {
        sem = new Semaphore(1)
      }
      sem.acquire()
    }

    void unlock() {
      if (sem == null) {
        throw new IllegalStateException("Attempt made to unlock mutex that was never locked")
      }
      sem.release()
    }
  }
}


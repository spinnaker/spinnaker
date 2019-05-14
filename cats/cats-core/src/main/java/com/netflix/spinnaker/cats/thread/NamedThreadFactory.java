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

package com.netflix.spinnaker.cats.thread;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public class NamedThreadFactory implements ThreadFactory {
  private final AtomicLong threadNumber = new AtomicLong();
  private final String baseName;

  public NamedThreadFactory(String baseName) {
    this.baseName = baseName;
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread t = Executors.defaultThreadFactory().newThread(r);
    t.setName(baseName + "-" + threadNumber.incrementAndGet());
    return t;
  }
}

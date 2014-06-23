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

package com.netflix.spinnaker.orca

import groovy.transform.CompileStatic
import com.google.common.collect.ForwardingMap
import com.google.common.collect.ImmutableMap

// TODO: should really move this to the orca-test subproject but that would create a circular dependency.
/**
 * A convenient implementation of {@link TaskContext} that just wraps a plain <em>Map</em>. Use this for testing.
 */
@CompileStatic
final class SimpleTaskContext extends ForwardingMap<String, Object> implements TaskContext {

  private final Map<String, Object> delegate = [:]

  @Override
  ImmutableMap<String, Object> getInputs() {
    ImmutableMap.copyOf(delegate)
  }

  @Override
  protected Map<String, Object> delegate() {
    delegate
  }
}

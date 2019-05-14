/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import groovy.lang.Closure;
import java.util.function.Function;
import java.util.function.Supplier;

public class PassThroughOperationPoller extends OperationPoller {
  public PassThroughOperationPoller() {
    super(0, 0);
  }

  @Override
  public Object waitForOperation(
      Closure operation,
      Closure ifDone,
      Long timeoutSeconds,
      Task task,
      String resourceString,
      String basePhase) {
    return operation.call();
  }

  @Override
  public <T> T waitForOperation(
      Supplier<T> operation,
      Function<T, Boolean> ifDone,
      Long timeoutSeconds,
      Task task,
      String resourceString,
      String basePhase) {
    return operation.get();
  }
}

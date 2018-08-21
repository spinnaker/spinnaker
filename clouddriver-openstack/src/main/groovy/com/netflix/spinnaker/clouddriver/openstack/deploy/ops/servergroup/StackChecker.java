/*
 * Copyright 2018 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup;

import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker;
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.heat.Stack;

/**
 * This class checks if an OpenStack stack is in a ready state.
 */
public class StackChecker implements BlockingStatusChecker.StatusChecker<Stack> {
  Operation operation;

  enum Operation {
    CREATE,
    UPDATE,
    DELETE
  }

  StackChecker (Operation operation) {
    this.operation = operation;
  }

  @Override
  public boolean isReady(Stack stack) {
    if (stack == null) {
      if (operation == Operation.DELETE) {
        return true;
      }
      ActionResponse actionResponse = ActionResponse.actionFailed("Cannot get state for null stack", 404);
      throw new OpenstackProviderException(actionResponse);
    }

    String status = stack.getStatus();
    String operationString = operation.toString();
    if ((operationString + "_IN_PROGRESS").equals(status)) {
      return false;
    } else if ((operationString + "_FAILED").equals(status)) {
      String message = String.format("Failed to %s stack %s: %s", operation.toString().toLowerCase(), stack.getName(), stack.getStackStatusReason());
      ActionResponse actionResponse = ActionResponse.actionFailed(message, 500);
      throw new OpenstackProviderException(actionResponse);
    } else if ((operationString + "_COMPLETE").equals(status)) {
      return true;
    } else {
      String message = String.format("Unknown status for stack %s: %s %s", stack.getName(), stack.getStatus(), stack.getStackStatusReason());
      ActionResponse actionResponse = ActionResponse.actionFailed(message, 500);
      throw new OpenstackProviderException(actionResponse);
    }
  }
}

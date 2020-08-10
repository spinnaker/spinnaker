/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import com.netflix.spinnaker.clouddriver.saga.flow.SagaExceptionHandler;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import io.grpc.StatusRuntimeException;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class TitusExceptionHandler implements SagaExceptionHandler {

  @Nonnull
  @Override
  public Exception handle(@Nonnull Exception exception) {
    if (exception instanceof StatusRuntimeException) {
      StatusRuntimeException statusRuntimeException = (StatusRuntimeException) exception;
      return new TitusException(statusRuntimeException, isRetryable(statusRuntimeException));
    }

    return exception;
  }

  private boolean isRetryable(StatusRuntimeException statusRuntimeException) {
    switch (statusRuntimeException.getStatus().getCode()) {
      case ABORTED:
      case DEADLINE_EXCEEDED:
      case INTERNAL:
      case RESOURCE_EXHAUSTED:
      case UNAVAILABLE:
      case UNKNOWN:
        return true;
      case INVALID_ARGUMENT:
        return invalidArgumentConditional(statusRuntimeException.getMessage());
      default:
        return false;
    }
  }

  private boolean invalidArgumentConditional(String statusRuntimeExceptionMessage) {
    if (statusRuntimeExceptionMessage == null) {
      return false;
    }

    // There is a particular operation in CloudDriver WRT attaching a server group to a load
    // balancer that does not behave as expected when retrying due to a rate exceeded error.
    // We will disable retrying on this specific error until that issue has been debugged and
    // resolved.
    // boolean rateExceeded = statusRuntimeExceptionMessage.toLowerCase().contains("rate exceeded");
    boolean assumeRoleError =
        statusRuntimeExceptionMessage.toLowerCase().contains("jobiamvalidator")
            && statusRuntimeExceptionMessage
                .toLowerCase()
                .contains("titus cannot assume into role");

    return assumeRoleError;
  }
}

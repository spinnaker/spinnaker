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
package com.netflix.spinnaker.kork.exceptions

import spock.lang.Specification

class ExceptionSummarySpec extends Specification {

  def "converts a chain of exceptions to a summary"() {
    given:
    Exception rootException = new ExternalLibraryException("INVALID_ARGUMENT: TitusServiceException: Image xyz/abcd:20190822_152019_934d150 does not exist in registry")
    Exception intermediateException = new CloudProviderException("Let's pretend it was caught elsewhere", rootException)
    Exception nonSpinnakerException = new RuntimeException("And here's another intermediate but it isn't SpinnakerException", intermediateException)
    Exception topException = new IntegrationException("Failed to apply action SubmitTitusJob for TitusDeployHandler/sha1", nonSpinnakerException)

    when:
    def result = ExceptionSummary.from(topException)

    then:
    result.message == "Failed to apply action SubmitTitusJob for TitusDeployHandler/sha1"
    result.cause == "INVALID_ARGUMENT: TitusServiceException: Image xyz/abcd:20190822_152019_934d150 does not exist in registry"
    result.retryable == null
    result.details.size() == 4
    result.details*.message == [
      "INVALID_ARGUMENT: TitusServiceException: Image xyz/abcd:20190822_152019_934d150 does not exist in registry",
      "Let's pretend it was caught elsewhere",
      "And here's another intermediate but it isn't SpinnakerException",
      "Failed to apply action SubmitTitusJob for TitusDeployHandler/sha1"
    ]
  }

  private static class ExternalLibraryException extends RuntimeException {
    ExternalLibraryException(String message) {
      super(message)
    }
  }
  private static class CloudProviderException extends IntegrationException {
    CloudProviderException(String message, Throwable cause) {
      super(message, cause)
    }
  }
}

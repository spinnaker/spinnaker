/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.kork.aws;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;

class InstrumentedRetrySupport {
  static String[] buildTags(AmazonWebServiceRequest originalRequest, AmazonClientException exception) {
    final AmazonServiceException ase = amazonServiceException(exception);

    final String[] tags = {
      "requestType", originalRequest.getClass().getSimpleName(),
      "statusCode", Integer.toString(ase.getStatusCode()),
      "errorCode", ase.getErrorCode(),
      "serviceName", ase.getServiceName(),
      "errorType", ase.getErrorType().name()
    };
    return tags;
  }

  private static AmazonServiceException amazonServiceException(AmazonClientException exception) {
    if (exception instanceof AmazonServiceException) {
      return (AmazonServiceException) exception;
    }

    final AmazonServiceException ase = new AmazonServiceException(exception.getMessage(), exception);
    ase.setStatusCode(-1);
    ase.setErrorCode("UNKNOWN");
    ase.setServiceName("UNKNOWN");
    ase.setErrorType(AmazonServiceException.ErrorType.Unknown);
    return ase;
  }

}

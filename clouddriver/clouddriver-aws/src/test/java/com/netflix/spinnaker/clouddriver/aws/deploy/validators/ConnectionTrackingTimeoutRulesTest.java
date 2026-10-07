/*
 * Copyright 2026 spinnaker.io
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
package com.netflix.spinnaker.clouddriver.aws.deploy.validators;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConnectionTrackingTimeoutRulesTest {

  private static final String PREFIX = "basicAmazonDeployDescription";

  @Test
  void unsetTimeoutsAreValid() {
    ValidationErrors errors = mock(ValidationErrors.class);

    ConnectionTrackingTimeoutRules.validate(PREFIX, null, null, null, errors);

    verifyNoInteractions(errors);
  }

  @ParameterizedTest
  @CsvSource({
    "tcpEstablishedTimeout, 60",
    "tcpEstablishedTimeout, 432000",
    "udpStreamTimeout, 60",
    "udpStreamTimeout, 180",
    "udpTimeout, 30",
    "udpTimeout, 60"
  })
  void boundaryValuesAreValid(String field, int value) {
    ValidationErrors errors = mock(ValidationErrors.class);

    validateOne(field, value, errors);

    verifyNoInteractions(errors);
  }

  @ParameterizedTest
  @CsvSource({
    "tcpEstablishedTimeout, 59",
    "tcpEstablishedTimeout, 432001",
    "udpStreamTimeout, 59",
    "udpStreamTimeout, 181",
    "udpTimeout, 29",
    "udpTimeout, 61"
  })
  void valuesOutsideTheEc2RangeAreRejected(String field, int value) {
    ValidationErrors errors = mock(ValidationErrors.class);

    validateOne(field, value, errors);

    verify(errors).rejectValue(eq(field), eq(PREFIX + "." + field + ".invalid"), anyString());
  }

  private static void validateOne(String field, int value, ValidationErrors errors) {
    Integer tcp = field.equals("tcpEstablishedTimeout") ? value : null;
    Integer udpStream = field.equals("udpStreamTimeout") ? value : null;
    Integer udp = field.equals("udpTimeout") ? value : null;
    ConnectionTrackingTimeoutRules.validate(PREFIX, tcp, udpStream, udp, errors);
  }
}

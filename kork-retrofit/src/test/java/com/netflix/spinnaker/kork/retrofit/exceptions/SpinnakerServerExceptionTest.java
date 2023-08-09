/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.kork.retrofit.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import retrofit.RetrofitError;

public class SpinnakerServerExceptionTest {
  private static final String CUSTOM_MESSAGE = "custom message";

  @Test
  public void testSpinnakerNetworkException_NewInstance() {
    IOException initialException = new IOException("message");
    try {
      RetrofitError error = RetrofitError.networkError("http://localhost", initialException);
      throw new SpinnakerNetworkException(error);
    } catch (SpinnakerException e) {
      SpinnakerException newException = e.newInstance(CUSTOM_MESSAGE);

      assertTrue(newException instanceof SpinnakerNetworkException);
      assertEquals(CUSTOM_MESSAGE, newException.getMessage());
      assertEquals(e, newException.getCause());
    }
  }

  @Test
  public void testSpinnakerServerException_NewInstance() {
    Throwable cause = new Throwable("message");
    try {
      RetrofitError error = RetrofitError.unexpectedError("http://localhost", cause);
      throw new SpinnakerServerException(error);
    } catch (SpinnakerException e) {
      SpinnakerException newException = e.newInstance(CUSTOM_MESSAGE);

      assertTrue(newException instanceof SpinnakerServerException);
      assertEquals(CUSTOM_MESSAGE, newException.getMessage());
      assertEquals(e, newException.getCause());
    }
  }
}

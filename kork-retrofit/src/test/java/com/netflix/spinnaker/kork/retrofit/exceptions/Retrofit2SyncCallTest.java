/*
 * Copyright 2023 OpsMx, Inc.
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

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

public class Retrofit2SyncCallTest {

  @Test
  public void testExecuteSuccss() throws IOException {
    Call<String> mockcall = Mockito.mock(Call.class);
    when(mockcall.execute()).thenReturn(Response.success("testing"));
    String execute = Retrofit2SyncCall.execute(mockcall);
    assertEquals("testing", execute);
  }

  @Test
  public void testExecuteThrowException() throws IOException {
    Call<String> mockcall = Mockito.mock(Call.class);
    IOException ioException = new IOException("exception test");
    when(mockcall.execute()).thenThrow(ioException);
    SpinnakerNetworkException networkEx =
        assertThrows(
            SpinnakerNetworkException.class,
            () -> {
              Retrofit2SyncCall.execute(mockcall);
            });
    assertEquals(ioException, networkEx.getCause());
  }
}

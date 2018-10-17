/*
 * Copyright (c) 2018 Nike, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */

package com.netflix.kayenta.signalfx.service;

import com.google.common.io.ByteStreams;
import org.junit.Test;
import retrofit.mime.TypedByteArray;
import retrofit.mime.TypedInput;

import java.io.InputStream;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class SignalFxRemoteServiceTest {

  @Test
  public void test_that_a_signalfx_signal_flow_response_can_be_parsed() throws Exception {
    InputStream response = getClass().getClassLoader().getResourceAsStream("signalfx-signalflow-response.text");
    SignalFxConverter converter = new SignalFxConverter();
    TypedInput typedInput = new TypedByteArray("text/plain", ByteStreams.toByteArray(response));
    SignalFlowExecutionResult signalFlowExecutionResult = (SignalFlowExecutionResult) converter.fromBody(typedInput, SignalFlowExecutionResult.class);

    assertNotNull(signalFlowExecutionResult);
    assertThat("The signalFlowExecutionResult contains the channel messages",
        signalFlowExecutionResult.getChannelMessages().size(), greaterThan(1));
  }
}

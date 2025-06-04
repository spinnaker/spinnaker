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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.io.ByteStreams;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class SignalFxRemoteServiceTest {

  @Test
  public void test_that_a_signalfx_signal_flow_response_can_be_parsed() throws Exception {
    InputStream response =
        getClass().getClassLoader().getResourceAsStream("signalfx-signalflow-response.text");
    SignalFxConverter converter = new SignalFxConverter();
    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl("http://signalfx")
            .addConverterFactory(JacksonConverterFactory.create())
            .build();
    Converter<ResponseBody, SignalFlowExecutionResult> signalfxResponseConverter =
        (Converter<ResponseBody, SignalFlowExecutionResult>)
            converter.responseBodyConverter(
                SignalFlowExecutionResult.class, new Annotation[0], retrofit);

    ResponseBody signalfxResponseBody =
        ResponseBody.create(MediaType.parse("text/plain"), ByteStreams.toByteArray(response));

    SignalFlowExecutionResult signalFlowExecutionResult =
        signalfxResponseConverter.convert(signalfxResponseBody);

    assertNotNull(signalFlowExecutionResult);
    assertThat(signalFlowExecutionResult.getChannelMessages().size())
        .describedAs("The signalFlowExecutionResult contains the channel messages")
        .isGreaterThan(1);
  }
}

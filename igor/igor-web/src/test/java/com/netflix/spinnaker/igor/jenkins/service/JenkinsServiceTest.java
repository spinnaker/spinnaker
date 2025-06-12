/*
 * Copyright 2025 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.jenkins.service;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient;
import com.netflix.spinnaker.igor.model.Crumb;
import com.netflix.spinnaker.igor.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class JenkinsServiceTest {

  @RegisterExtension
  static final WireMockExtension wmJenkins =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static JenkinsClient jenkinsClient;
  static JenkinsService jenkinsService;
  static ObjectMapper objectMapper;

  @BeforeAll
  public static void setup() {
    objectMapper =
        new XmlMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JaxbAnnotationModule());
    jenkinsClient =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(wmJenkins.baseUrl()))
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create(objectMapper))
            .build()
            .create(JenkinsClient.class);
    CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
    jenkinsService =
        new JenkinsService(
            RetrofitUtils.getBaseUrl(wmJenkins.baseUrl()),
            jenkinsClient,
            true,
            Permissions.EMPTY,
            circuitBreakerRegistry);
  }

  @Test
  public void testJenkinsJobBuild() throws JsonProcessingException {
    Crumb crumb = new Crumb();
    crumb.setCrumb("crumb");
    wmJenkins.stubFor(
        WireMock.get("/crumbIssuer/api/xml")
            .willReturn(WireMock.aResponse().withBody(objectMapper.writeValueAsString(crumb))));

    wmJenkins.stubFor(
        WireMock.post("/job/job1/build").willReturn(WireMock.aResponse().withStatus(201)));

    // TODO: fix this issue
    Throwable thrown = catchThrowable(() -> jenkinsService.build("job1"));
    assertThat(thrown).isInstanceOf(SpinnakerConversionException.class);
    assertThat(thrown.getMessage())
        .contains(
            "Failed to process response body: Unexpected EOF in prolog\n"
                + " at [row,col {unknown-source}]: [1,0]");
  }
}

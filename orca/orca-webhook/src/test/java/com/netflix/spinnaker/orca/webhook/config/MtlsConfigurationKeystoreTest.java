/*
 * Copyright 2024 Apple, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.webhook.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.config.OkHttpClientComponents;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.webhook.service.WebhookService;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

@SpringBootTest(
    classes = {
      MtlsConfigurationKeystoreTest.KeyStoreTestConfiguration.class,
      MtlsConfigurationTestBase.TestConfigurationBase.class,
      OkHttp3ClientConfiguration.class,
      OkHttpClientComponents.class,
      WebhookConfiguration.class,
      WebhookService.class
    })
public class MtlsConfigurationKeystoreTest extends MtlsConfigurationTestBase {
  static class KeyStoreTestConfiguration {
    @Bean
    @Primary
    WebhookProperties webhookProperties() {
      // Set up identity and trust properties
      var props = new WebhookProperties();

      var identity = new WebhookProperties.IdentitySettings();
      identity.setEnabled(true);
      identity.setIdentityStore(clientIdentityStoreFile.getAbsolutePath());
      identity.setIdentityStorePassword(password);
      identity.setIdentityStoreKeyPassword(password);
      props.setIdentity(identity);

      var trust = new WebhookProperties.TrustSettings();
      trust.setEnabled(true);
      trust.setTrustStore(caStoreFile.getAbsolutePath());
      trust.setTrustStorePassword(password);
      props.setTrust(trust);

      // Tell okhttp to skip hostname verification, since all this is made up
      props.setInsecureSkipHostnameVerification(true);

      return props;
    }
  }

  @Autowired WebhookService service;

  @Test
  @SneakyThrows
  public void mTLSConnectivityTest() {
    var context =
        new HashMap<String, Object>() {
          {
            put("url", mockWebServer.url("/").toString());
            put("method", HttpMethod.POST);
            put("payload", "{ \"foo\": \"bar\" }");
          }
        };

    var stageExecution = new StageExecutionImpl(null, null, null, context);
    var response = service.callWebhook(stageExecution);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    var body = mapper.readValue(response.getBody().toString(), Map.class);
    assertEquals("yep", body.get("mtls"));
  }
}

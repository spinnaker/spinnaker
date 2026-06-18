/*
 * Copyright 2020 Cerner Corporation
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
 */

package com.netflix.spinnaker.echo.microsoftteams;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Slf4j
public class MicrosoftTeamsService {
  private final OkHttp3ClientConfiguration okHttp3ClientConfiguration;

  public MicrosoftTeamsService(OkHttp3ClientConfiguration okHttp3ClientConfiguration) {
    this.okHttp3ClientConfiguration = okHttp3ClientConfiguration;
  }

  /**
   * Sends a pre-rendered JSON message to Microsoft Teams webhook.
   *
   * @param webhookUrl The Teams webhook URL
   * @param jsonMessage The JSON message as a string
   * @return The response from the webhook
   */
  public ResponseBody sendMessage(String webhookUrl, String jsonMessage) {
    // Check for legacy webhook URL and warn user
    checkForLegacyWebhookUrl(webhookUrl);

    MicrosoftTeamsClient microsoftTeamsClient =
        new Retrofit.Builder()
            .baseUrl(HttpUrl.get(webhookUrl).resolve("/").toString())
            .client(okHttp3ClientConfiguration.createForRetrofit2().build())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(MicrosoftTeamsClient.class);

    return Retrofit2SyncCall.execute(microsoftTeamsClient.sendMessage(webhookUrl, RequestBody.create(jsonMessage, MediaType.parse("application/json"))));
  }

  /**
   * Checks if the webhook URL is a legacy Office 365 Connector URL and logs a warning.
   *
   * <p>Office 365 Connectors are being retired on March 31, 2026. Users must migrate to Power
   * Automate Workflows webhooks.
   *
   * @param webhookUrl The webhook URL to check
   */
  private void checkForLegacyWebhookUrl(String webhookUrl) {
    if (webhookUrl != null
        && (webhookUrl.contains("outlook.office.com/webhook")
            || webhookUrl.contains("office365.com"))) {
      log.warn(
          "DEPRECATED: Microsoft Teams webhook URL appears to be a legacy Office 365 Connector. "
              + "Office 365 Connectors are being retired on March 31, 2026. "
              + "Please migrate to Power Automate Workflows webhooks. "
              + "See https://devblogs.microsoft.com/microsoft365dev/retirement-of-office-365-connectors-within-microsoft-teams/ "
              + "Webhook URL: {}",
          maskWebhookUrl(webhookUrl));
    }
  }

  /**
   * Masks sensitive parts of the webhook URL for logging purposes.
   *
   * @param webhookUrl The webhook URL to mask
   * @return Masked URL showing only the domain
   */
  private String maskWebhookUrl(String webhookUrl) {
    try {
      HttpUrl url = HttpUrl.get(webhookUrl);
      return url.scheme() + "://" + url.host() + "/...";
    } catch (Exception e) {
      return "[invalid URL]";
    }
  }
}

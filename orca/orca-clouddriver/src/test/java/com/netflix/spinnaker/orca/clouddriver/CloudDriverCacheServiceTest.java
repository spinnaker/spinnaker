package com.netflix.spinnaker.orca.clouddriver;

import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class CloudDriverCacheServiceTest {
  @RegisterExtension
  static WireMockExtension wmCache =
      WireMockExtension.newInstance().options(new WireMockConfiguration().dynamicPort()).build();

  private static CloudDriverCacheService cacheService;

  @BeforeAll
  public static void setup() {
    cacheService =
        new Retrofit.Builder()
            .baseUrl(wmCache.baseUrl())
            .client(new OkHttpClient())
            .addConverterFactory(JacksonConverterFactory.create(new ObjectMapper()))
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .build()
            .create(CloudDriverCacheService.class);
  }

  @Test
  public void verifyForceCacheUpdate() throws IOException {
    wmCache.stubFor(
        WireMock.post(urlMatching("/cache/aws/CloudFormation"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody("{\"status\":\"ok\"}")));

    Retrofit2SyncCall.execute(
        cacheService.forceCacheUpdate(
            "aws", "CloudFormation", Map.of("id", "1", "region", List.of("us-east-1"))));

    wmCache.verify(1, WireMock.postRequestedFor(urlMatching("/cache/aws/CloudFormation")));
  }
}

package com.netflix.spinnaker.igor.helm.accounts;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.igor.config.HelmConverterFactory;
import com.netflix.spinnaker.igor.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;

public class HelmAccountsServiceTest {

  @RegisterExtension
  static WireMockExtension wmHelmAccounts =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static HelmAccountsService helmAccountsService;

  @BeforeAll
  public static void setup() {
    helmAccountsService =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(wmHelmAccounts.baseUrl()))
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(new HelmConverterFactory())
            .build()
            .create(HelmAccountsService.class);
  }

  @Test
  public void testGetAllAccounts_with_additionalFields() {
    // Helm account with an addition field "type"
    String expectedResponse =
        "[{\"name\":\"acc1\",\"types\":[\"type1\",\"type2\"],\"type\":\"helm\"}]";

    wmHelmAccounts.stubFor(
        WireMock.get("/artifacts/credentials").willReturn(aResponse().withBody(expectedResponse)));

    // TODO: Fix this error. There shouldn't be any error due to additional fields in the helm
    // accounts
    Throwable thrown =
        catchThrowable(() -> Retrofit2SyncCall.execute(helmAccountsService.getAllAccounts()));
    assertThat(thrown).isInstanceOf(SpinnakerConversionException.class);
    assertThat(thrown.getMessage())
        .contains(
            "Failed to process response body: Unrecognized field \"type\" (class com.netflix.spinnaker.igor.helm.accounts.ArtifactAccount), not marked as ignorable (2 known properties: \"types\", \"name\"])");
  }
}

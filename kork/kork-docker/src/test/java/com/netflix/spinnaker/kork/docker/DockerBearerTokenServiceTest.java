package com.netflix.spinnaker.kork.docker;

import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.kork.docker.model.DockerBearerToken;
import com.netflix.spinnaker.kork.docker.service.DockerBearerTokenService;
import com.netflix.spinnaker.config.DefaultServiceClientProvider;
import com.netflix.spinnaker.kork.docker.service.RegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import retrofit2.Call;
import retrofit2.Response;

import java.io.*;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(
  classes = {
    com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties.class,
    com.netflix.spinnaker.kork.client.ServiceClientProvider.class,
    com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider.class,
    com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor.class,
    okhttp3.OkHttpClient.class,
    com.netflix.spinnaker.config.DefaultServiceClientProvider.class,
    com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider.class,
    com.fasterxml.jackson.databind.ObjectMapper.class
  },
  webEnvironment = SpringBootTest.WebEnvironment.NONE
)
public class DockerBearerTokenServiceTest {

  private static final String REALM1 = "https://auth.docker.io";
  private static final String PATH1 = "token";
  private static final String SERVICE1 = "registry.docker.io";
  private static final String SCOPE1 = "repository:library/ubuntu:push,pull";
  private static final String SCOPE2 = "repository:library/ubuntu:push";
  private static final String REPOSITORY1 = "library/ubuntu";

  @MockBean
  DefaultServiceClientProvider serviceClientProvider;

  DockerBearerTokenService tokenService;

  @BeforeEach
  void setUp() throws IOException {
    DefaultServiceClientProvider serviceClientProvider = Mockito.mock(DefaultServiceClientProvider.class);
    RegistryService registryService = Mockito.mock(RegistryService.class);

    // Create a mock Call object
    Call<DockerBearerToken> mockCall = Mockito.mock(Call.class);

    // Stub execute() to return a dummy response
    DockerBearerToken dummyToken = new DockerBearerToken();
    dummyToken.setToken("dummy-token");
    Response<DockerBearerToken> response = Response.success(dummyToken);

    when(mockCall.execute()).thenReturn(response);
    // Stub registryService.getToken(...) to return the mock Call
    when(registryService.getToken(anyString(), anyString(), anyString(), anyString()))
      .thenReturn(mockCall);

    when(serviceClientProvider.getService(eq(RegistryService.class), any(DefaultServiceEndpoint.class)))
      .thenReturn(registryService);

    tokenService = new DockerBearerTokenService(serviceClientProvider);
  }

  @Test
  void shouldParseWwwAuthenticateHeaderWithFullPrivilegesAndPath() {
    String input = String.format("realm=\"%s/%s\",service=\"%s\",scope=\"%s\"", REALM1, PATH1, SERVICE1, SCOPE1);
    var result = tokenService.parseBearerAuthenticateHeader(input);
    assertEquals(PATH1, result.getPath());
    assertEquals(REALM1, result.getRealm());
    assertEquals(SERVICE1, result.getService());
    assertEquals(SCOPE1, result.getScope());
  }

  @Test
  void shouldParseWwwAuthenticateHeaderWithMissingServiceAndPath() {
    String input = String.format("realm=\"%s/%s\",scope=\"%s\"", REALM1, PATH1, SCOPE1);
    var result = tokenService.parseBearerAuthenticateHeader(input);
    assertEquals(PATH1, result.getPath());
    assertEquals(REALM1, result.getRealm());
    assertNull(result.getService());
    assertEquals(SCOPE1, result.getScope());
  }

  @Test
  void shouldParseWwwAuthenticateHeaderWithSomePrivilegesAndPath() {
    String input = String.format("realm=\"%s/%s\",service=\"%s\",scope=\"%s\"", REALM1, PATH1, SERVICE1, SCOPE2);
    var result = tokenService.parseBearerAuthenticateHeader(input);
    assertEquals(PATH1, result.getPath());
    assertEquals(REALM1, result.getRealm());
    assertEquals(SERVICE1, result.getService());
    assertEquals(SCOPE2, result.getScope());
  }

  @Test
  void shouldParseWwwAuthenticateHeaderWithSomePrivilegesAndNoPath() {
    String input = String.format("realm=\"%s\",service=\"%s\",scope=\"%s\"", REALM1, SERVICE1, SCOPE2);
    var result = tokenService.parseBearerAuthenticateHeader(input);
    assertEquals(result.getPath(),"");
    assertEquals(REALM1, result.getRealm());
    assertEquals(SERVICE1, result.getService());
    assertEquals(SCOPE2, result.getScope());
  }

  @Test
  void shouldParseWwwAuthenticateHeaderWithMissingServiceAndNoPath() {
    String input = String.format("realm=\"%s\",scope=\"%s\"", REALM1, SCOPE2);
    var result = tokenService.parseBearerAuthenticateHeader(input);
    assertEquals(result.getPath(), "");
    assertEquals(REALM1, result.getRealm());
    assertNull(result.getService());
    assertEquals(SCOPE2, result.getScope());
  }

  @Test
  void shouldParseUnquotedWwwAuthenticateHeaderWithSomePrivilegesAndPath() {
    String input = String.format("realm=%s/%s,service=%s,scope=%s", REALM1, PATH1, SERVICE1, SCOPE2);
    var result = tokenService.parseBearerAuthenticateHeader(input);
    assertEquals(PATH1, result.getPath());
    assertEquals(REALM1, result.getRealm());
    assertEquals(SERVICE1, result.getService());
    assertEquals(SCOPE2, result.getScope());
  }

  @Test
  void shouldRequestARealTokenFromDockerhubTokenRegistry() {
    String authenticateHeader = String.format("realm=\"%s/%s\",service=\"%s\",scope=\"%s\"", REALM1, PATH1, SERVICE1, SCOPE1);
    DockerBearerToken token = tokenService.getToken(REPOSITORY1, authenticateHeader);
    assertNotNull(token);
    assertTrue(token.getToken().length() > 0);
  }

  @Test
  void shouldRequestARealTokenAndSupplyCachedOne() {
    String authenticateHeader = String.format("realm=\"%s/%s\",service=\"%s\",scope=\"%s\"", REALM1, PATH1, SERVICE1, SCOPE1);
    tokenService.getToken(REPOSITORY1, authenticateHeader);
    DockerBearerToken token = tokenService.getToken(REPOSITORY1);
    assertNotNull(token);
    assertTrue(token.getToken().length() > 0);
  }

  @Test
  void shouldReadPasswordFromFileAndPrepareBasicAuthString() throws IOException {
    File passwordFile = new File("src/test/resources/password.txt");
    String username = "username";
    String passwordContents;
    try (BufferedReader reader = new BufferedReader(new FileReader(passwordFile))) {
      passwordContents = reader.readLine().trim();
    }
    DockerBearerTokenService fileTokenService = new DockerBearerTokenService(username, passwordFile, serviceClientProvider);
    String basicAuth = new String(Base64.getDecoder().decode(fileTokenService.getBasicAuth().getBytes()));
    assertEquals(username + ":" + passwordContents, basicAuth);
  }

  @Test
  void shouldRunCommandToGetPasswordAndPrepareBasicAuthString() {
    String passwordCommand = "echo hunter2";
    String username = "username";
    String password = "";
    String actualPassword = "hunter2";
    DockerBearerTokenService passwordCommandService = new DockerBearerTokenService(username, password, passwordCommand, serviceClientProvider);
    String basicAuth = new String(Base64.getDecoder().decode(passwordCommandService.getBasicAuth().getBytes()));
    assertEquals(username + ":" + actualPassword, basicAuth);
  }
}

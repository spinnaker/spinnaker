/*
 * Copyright 2026 Salesforce, Inc.
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

package com.netflix.spinnaker.kork.artifacts.artifactstoretest;

import static com.netflix.spinnaker.kork.artifacts.artifactstore.s3.S3ArtifactStore.ENFORCE_PERMS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.hash.Hashing;
import com.netflix.spinnaker.config.ErrorConfiguration;
import com.netflix.spinnaker.config.RetrofitErrorConfiguration;
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreConfigurationProperties;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreGetter;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreStorer;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURIBuilder;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStoreURISHA256Builder;
import com.netflix.spinnaker.kork.artifacts.artifactstore.filters.ApplicationStorageFilter;
import com.netflix.spinnaker.kork.artifacts.artifactstore.s3.S3ArtifactStoreConfiguration;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.UserPermissionEvaluator;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.GetObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;

/**
 * Test how AWS SDK exceptions thrown by S3ArtifactStoreGetter and S3ArtifactStoreStorer are
 * serialized. Test the success cases too.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "artifact-store.type=s3",
      "artifact-store.s3.bucket=my-bucket",
      "artifact-store.s3.enabled=true"
    },
    classes = {
      ErrorConfiguration.class,
      RetrofitErrorConfiguration.class,
      S3ArtifactStoreConfiguration.class,
      S3ArtifactStoreControllerTest.TestControllerConfiguration.class
    })
class S3ArtifactStoreControllerTest {

  private static final String APPLICATION = "my-application";
  private static final String USER = "my-user";
  private static final String STORE_REFERENCE =
      Base64.getEncoder().encodeToString("hello world".getBytes(StandardCharsets.UTF_8));
  private static final String STORE_ARTIFACT_REFERENCE =
      "ref://"
          + APPLICATION
          + "/"
          + Hashing.sha256().hashBytes(STORE_REFERENCE.getBytes(StandardCharsets.UTF_8)).toString();

  private static final String S3_GET_ERROR_MESSAGE =
      "artifact failed to be retrieved: bucket=my-bucket ref=ref://some-artifact";

  private static final String S3_STORE_ERROR_MESSAGE =
      "artifact failed to be stored: bucket=my-bucket ref=" + STORE_ARTIFACT_REFERENCE;

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  @Configuration
  @EnableAutoConfiguration
  @EnableWebSecurity
  @EnableConfigurationProperties(ArtifactStoreConfigurationProperties.class)
  static class TestControllerConfiguration implements WebMvcConfigurer {
    @Bean
    protected SecurityFilterChain configure(HttpSecurity http) throws Exception {
      http.csrf(csrf -> csrf.disable()).headers(headers -> headers.disable());
      http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
      http.anonymous(anon -> anon.disable());
      return http.build();
    }

    @Bean
    FilterRegistrationBean<AuthenticatedRequestFilter> authenticatedRequestFilter() {
      return new FilterRegistrationBean<>(
          new AuthenticatedRequestFilter(/* extractSpinnakerHeaders= */ true));
    }

    @Bean
    Map<String, List<ApplicationStorageFilter>> excludeFilters() {
      return Map.of();
    }

    @Bean
    ArtifactStoreURIBuilder artifactStoreURIBuilder() {
      return new ArtifactStoreURISHA256Builder();
    }

    @Bean
    ArtifactController artifactController(ArtifactStoreGetter getter, ArtifactStoreStorer storer) {
      return new ArtifactController(getter, storer);
    }
  }

  @RestController
  @RequiredArgsConstructor
  static class ArtifactController {
    private final ArtifactStoreGetter getter;
    private final ArtifactStoreStorer storer;

    @GetMapping("/get-artifact")
    Artifact get() {
      return getter.get(ArtifactReferenceURI.parse("ref://some-artifact"));
    }

    @GetMapping("/store-artifact")
    Artifact store() {
      Artifact artifact =
          Artifact.builder()
              .type(ArtifactTypes.EMBEDDED_BASE64.getMimeType())
              .reference(STORE_REFERENCE)
              .build();
      return storer.store(artifact);
    }
  }

  @Autowired private TestRestTemplate restTemplate;

  @MockBean private S3Client s3Client;
  @MockBean private UserPermissionEvaluator userPermissionEvaluator;

  private HttpEntity<Void> getRequestEntity;
  private HttpEntity<Void> storeRequestEntity;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    HttpHeaders getHeaders = new HttpHeaders();
    getHeaders.set(Header.USER.getHeader(), USER);
    getRequestEntity = new HttpEntity<>(getHeaders);

    HttpHeaders storeHeaders = new HttpHeaders();
    storeHeaders.set(Header.APPLICATION.getHeader(), APPLICATION);
    storeRequestEntity = new HttpEntity<>(storeHeaders);

    Tag tag = Tag.builder().key(ENFORCE_PERMS_KEY).value(APPLICATION).build();
    GetObjectTaggingResponse taggingResponse =
        GetObjectTaggingResponse.builder().tagSet(List.of(tag)).build();
    when(s3Client.getObjectTagging(any(GetObjectTaggingRequest.class))).thenReturn(taggingResponse);

    when(userPermissionEvaluator.hasPermission(
            eq(USER), eq(APPLICATION), eq("application"), eq("READ")))
        .thenReturn(true);
  }

  @Test
  void testSuccessfulGetReturnsArtifact() throws Exception {
    byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
    GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
    ResponseBytes<GetObjectResponse> responseBytes =
        ResponseBytes.fromByteArray(getObjectResponse, content);
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange("/get-artifact", HttpMethod.GET, getRequestEntity, MAP_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("type", ArtifactTypes.REMOTE_BASE64.getMimeType())
        .containsEntry("reference", Base64.getEncoder().encodeToString(content));
  }

  @Test
  void testS3NoSuchKey() throws Exception {
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(
            NoSuchKeyException.builder()
                .statusCode(404)
                .message("The specified key does not exist.")
                .build());

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange("/get-artifact", HttpMethod.GET, getRequestEntity, MAP_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody())
        .containsKey("timestamp")
        .containsEntry("status", 404)
        .containsEntry("error", "Not Found")
        .containsEntry("exception", "org.springframework.web.server.ResponseStatusException")
        .containsEntry("message", S3_GET_ERROR_MESSAGE);
  }

  @Test
  void testS3AccessDenied() throws Exception {
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(403).message("Access Denied").build());

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange("/get-artifact", HttpMethod.GET, getRequestEntity, MAP_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody())
        .containsKey("timestamp")
        .containsEntry("status", 403)
        .containsEntry("error", "Forbidden")
        .containsEntry("exception", "org.springframework.web.server.ResponseStatusException")
        .containsEntry("message", S3_GET_ERROR_MESSAGE);
  }

  @Test
  void testS3InternalError() throws Exception {
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(500).message("Internal Error").build());

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange("/get-artifact", HttpMethod.GET, getRequestEntity, MAP_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody())
        .containsKey("timestamp")
        .containsEntry("status", 500)
        .containsEntry("error", "Internal Server Error")
        .containsEntry("exception", "org.springframework.web.server.ResponseStatusException")
        .containsEntry("message", S3_GET_ERROR_MESSAGE);
  }

  @Test
  void testGetRuntimeException() throws Exception {
    when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
        .thenThrow(new RuntimeException("something unexpected"));

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange("/get-artifact", HttpMethod.GET, getRequestEntity, MAP_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody())
        .containsKey("timestamp")
        .containsEntry("status", 500)
        .containsEntry("error", "Internal Server Error")
        .containsEntry("exception", "java.lang.RuntimeException")
        .containsEntry("message", S3_GET_ERROR_MESSAGE);
  }

  // ---- S3ArtifactStoreStorer tests ----

  @Test
  void testSuccessfulStoreReturnsArtifact() throws Exception {
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(ListObjectsV2Response.builder().build());

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange("/store-artifact", HttpMethod.GET, storeRequestEntity, MAP_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("type", ArtifactTypes.REMOTE_BASE64.getMimeType())
        .containsEntry("reference", STORE_ARTIFACT_REFERENCE);
  }

  @Test
  void testStoreS3PutObjectException() throws Exception {
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(ListObjectsV2Response.builder().build());
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(S3Exception.builder().statusCode(403).message("Access Denied").build());

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange("/store-artifact", HttpMethod.GET, storeRequestEntity, MAP_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody())
        .containsKey("timestamp")
        .containsEntry("status", 403)
        .containsEntry("error", "Forbidden")
        .containsEntry("exception", "org.springframework.web.server.ResponseStatusException")
        .containsEntry("message", S3_STORE_ERROR_MESSAGE);
  }

  @Test
  void testStoreS3ListObjectsException() throws Exception {
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenThrow(S3Exception.builder().statusCode(500).message("Internal Error").build());

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange("/store-artifact", HttpMethod.GET, storeRequestEntity, MAP_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody())
        .containsKey("timestamp")
        .containsEntry("status", 500)
        .containsEntry("error", "Internal Server Error")
        .containsEntry("exception", "org.springframework.web.server.ResponseStatusException")
        .containsEntry("message", S3_STORE_ERROR_MESSAGE);
  }

  @Test
  void testStoreRuntimeException() throws Exception {
    when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
        .thenReturn(ListObjectsV2Response.builder().build());
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(new RuntimeException("something unexpected"));

    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange("/store-artifact", HttpMethod.GET, storeRequestEntity, MAP_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody())
        .containsKey("timestamp")
        .containsEntry("status", 500)
        .containsEntry("error", "Internal Server Error")
        .containsEntry("exception", "java.lang.RuntimeException")
        .containsEntry("message", S3_STORE_ERROR_MESSAGE);
  }
}

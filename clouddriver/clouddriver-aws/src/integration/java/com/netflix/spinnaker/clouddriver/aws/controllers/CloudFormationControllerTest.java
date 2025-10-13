/*
 * Copyright 2025 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.aws.controllers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CompositeCache;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.mem.InMemoryCache;
import com.netflix.spinnaker.clouddriver.Main;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonCloudFormationProvider;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

@Import(CloudFormationControllerTest.CloudFormationTestConfig.class)
@SpringBootTest(
    classes = {Main.class, CloudFormationController.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.config.location = classpath:clouddriver.yml",
      "aws.enabled = false"
    }) // disabled aws to suppress NPE logs
public class CloudFormationControllerTest {

  static String stackId =
      "arn:aws:cloudformation:us-west-2:123456789012:stack/test-stack/abcde12345";
  static String id = "aws:stacks:123456789012:us-west-2:" + stackId;

  @LocalServerPort int port;

  @Test
  public void testCloudFormationStackApi() {
    OortService oortService = getOortService();
    Map cloudFormationStack =
        Retrofit2SyncCall.execute(oortService.getCloudFormationStack(stackId));
    assertThat(cloudFormationStack.get("stackId")).isEqualTo(stackId);
  }

  private OortService getOortService() {
    return new Retrofit.Builder()
        .baseUrl("http://localhost:" + port)
        .client(
            new okhttp3.OkHttpClient.Builder()
                .addInterceptor(new Retrofit2EncodeCorrectionInterceptor())
                .build())
        .addConverterFactory(JacksonConverterFactory.create())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .build()
        .create(OortService.class);
  }

  public interface OortService {
    @GET("aws/cloudFormation/stacks/stack")
    Call<Map> getCloudFormationStack(@Query("stackId") String stackId);
  }

  @TestConfiguration
  public static class CloudFormationTestConfig {
    @Bean
    @Primary
    public Cache cache() {
      WriteableCache cache = new InMemoryCache();
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("stackId", stackId);
      CacheData cacheData = new DefaultCacheData(id, attributes, new HashMap<>());
      cache.merge("stacks", cacheData);
      return new CompositeCache(List.of(cache));
    }

    @Bean
    public AmazonCloudFormationProvider amazonCloudFormationProvider(Cache cache) {
      return new AmazonCloudFormationProvider(cache, new ObjectMapper());
    }
  }
}

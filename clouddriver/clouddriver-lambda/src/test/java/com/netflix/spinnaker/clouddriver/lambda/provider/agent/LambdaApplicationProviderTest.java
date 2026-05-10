/*
 * Copyright 2026 Harness, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.provider.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.lambda.cache.Keys;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaApplication;
import com.netflix.spinnaker.clouddriver.model.Application;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class LambdaApplicationProviderTest {

  @Test
  public void shouldGetAllApplicationsFromCache() {
    Cache cache = mock(Cache.class);

    String appKey1 = Keys.getApplicationKey("app1");
    String appKey2 = Keys.getApplicationKey("app2");

    when(cache.filterIdentifiers(
            eq(Keys.Namespace.LAMBDA_APPLICATIONS.ns), eq("aws:lambdaApplications:*")))
        .thenReturn(List.of(appKey1, appKey2));

    CacheData cacheData1 =
        new DefaultCacheData(
            appKey1, ImmutableMap.of("attribute1", "value1"), Collections.emptyMap());
    CacheData cacheData2 =
        new DefaultCacheData(
            appKey2, ImmutableMap.of("attribute2", "value2"), Collections.emptyMap());

    when(cache.getAll(Keys.Namespace.LAMBDA_APPLICATIONS.ns, List.of(appKey1, appKey2)))
        .thenReturn(List.of(cacheData1, cacheData2));

    LambdaApplicationProvider provider = new LambdaApplicationProvider(cache);
    Set<? extends Application> applications = provider.getApplications(false);

    assertThat(applications).hasSize(2);
    assertThat(applications.stream().map(Application::getName))
        .containsExactlyInAnyOrder("app1", "app2");

    verify(cache)
        .filterIdentifiers(Keys.Namespace.LAMBDA_APPLICATIONS.ns, "aws:lambdaApplications:*");
    verify(cache).getAll(Keys.Namespace.LAMBDA_APPLICATIONS.ns, List.of(appKey1, appKey2));
  }

  @Test
  public void shouldReturnEmptySetWhenNoApplicationsExist() {
    Cache cache = mock(Cache.class);

    when(cache.filterIdentifiers(
            eq(Keys.Namespace.LAMBDA_APPLICATIONS.ns), eq("aws:lambdaApplications:*")))
        .thenReturn(Collections.emptyList());

    when(cache.getAll(eq(Keys.Namespace.LAMBDA_APPLICATIONS.ns), any(Collection.class)))
        .thenReturn(Collections.emptyList());

    LambdaApplicationProvider provider = new LambdaApplicationProvider(cache);
    Set<? extends Application> applications = provider.getApplications(true);

    assertThat(applications).isEmpty();

    verify(cache)
        .filterIdentifiers(Keys.Namespace.LAMBDA_APPLICATIONS.ns, "aws:lambdaApplications:*");
  }

  @Test
  public void shouldGetApplicationByName() {
    Cache cache = mock(Cache.class);
    String appName = "myapp";
    String appKey = Keys.getApplicationKey(appName);

    Map<String, Object> attributes = ImmutableMap.of("key1", "value1", "key2", 123);
    CacheData cacheData = new DefaultCacheData(appKey, attributes, Collections.emptyMap());

    when(cache.get(Keys.Namespace.LAMBDA_APPLICATIONS.ns, appKey)).thenReturn(cacheData);

    LambdaApplicationProvider provider = new LambdaApplicationProvider(cache);
    Application application = provider.getApplication(appName);

    assertThat(application).isNotNull();
    assertThat(application.getName()).isEqualTo(appName);
    assertThat(application.getAttributes()).containsEntry("key1", "value1");
    assertThat(application.getAttributes()).containsEntry("key2", "123");

    verify(cache).get(Keys.Namespace.LAMBDA_APPLICATIONS.ns, appKey);
  }

  @Test
  public void shouldReturnNullWhenApplicationDoesNotExist() {
    Cache cache = mock(Cache.class);
    String appName = "nonexistent";
    String appKey = Keys.getApplicationKey(appName);

    when(cache.get(Keys.Namespace.LAMBDA_APPLICATIONS.ns, appKey)).thenReturn(null);

    LambdaApplicationProvider provider = new LambdaApplicationProvider(cache);
    Application application = provider.getApplication(appName);

    assertThat(application).isNull();

    verify(cache).get(Keys.Namespace.LAMBDA_APPLICATIONS.ns, appKey);
  }

  @Test
  public void shouldMapCacheDataToLambdaApplication() {
    String appKey = Keys.getApplicationKey("testapp");
    Map<String, Object> attributes =
        ImmutableMap.of("region", "us-west-2", "accountId", "123456789", "count", 42);

    CacheData cacheData = new DefaultCacheData(appKey, attributes, Collections.emptyMap());

    LambdaApplication app = LambdaApplicationProvider.mapCacheDataToLambdaApplication(cacheData);

    assertThat(app.getName()).isEqualTo("testapp");
    assertThat(app.getAttributes()).hasSize(3);
    assertThat(app.getAttributes()).containsEntry("region", "us-west-2");
    assertThat(app.getAttributes()).containsEntry("accountId", "123456789");
    assertThat(app.getAttributes()).containsEntry("count", "42");
  }

  @Test
  public void shouldHandleEmptyAttributesInCacheData() {
    String appKey = Keys.getApplicationKey("emptyapp");
    CacheData cacheData =
        new DefaultCacheData(appKey, Collections.emptyMap(), Collections.emptyMap());

    LambdaApplication app = LambdaApplicationProvider.mapCacheDataToLambdaApplication(cacheData);

    assertThat(app.getName()).isEqualTo("emptyapp");
    assertThat(app.getAttributes()).isEmpty();
  }

  @Test
  public void shouldConvertAllAttributesToStrings() {
    String appKey = Keys.getApplicationKey("typetest");
    Map<String, Object> attributes =
        ImmutableMap.of(
            "stringValue", "text", "intValue", 100, "boolValue", true, "doubleValue", 3.14);

    CacheData cacheData = new DefaultCacheData(appKey, attributes, Collections.emptyMap());

    LambdaApplication app = LambdaApplicationProvider.mapCacheDataToLambdaApplication(cacheData);

    assertThat(app.getAttributes()).containsEntry("stringValue", "text");
    assertThat(app.getAttributes()).containsEntry("intValue", "100");
    assertThat(app.getAttributes()).containsEntry("boolValue", "true");
    assertThat(app.getAttributes()).containsEntry("doubleValue", "3.14");
  }
}

package com.netflix.spinnaker.clouddriver.lambda.provider.view;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.lambda.cache.Keys.Namespace.LAMBDA_FUNCTIONS;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.lambda.deploy.ops.LambdaTestingDefaults;
import com.netflix.spinnaker.clouddriver.model.Function;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LambdaFunctionProviderTest implements LambdaTestingDefaults {

  @Test
  void getApplicationFunctionsWithApp() {
    String applicationName = "lambdaTest";
    String appKey =
        com.netflix.spinnaker.clouddriver.aws.data.Keys.getApplicationKey(applicationName);
    String functionKey = "functionKey";

    Cache cache = mock(Cache.class);
    when(cache.get(APPLICATIONS.ns, appKey))
        .thenReturn(
            new DefaultCacheData(
                appKey, emptyMap(), ImmutableMap.of(LAMBDA_FUNCTIONS.ns, List.of(functionKey))));

    when(cache.get(LAMBDA_FUNCTIONS.ns, functionKey))
        .thenReturn(
            new DefaultCacheData(
                appKey, ImmutableMap.of(LAMBDA_FUNCTIONS.ns, functionKey), emptyMap()));

    Set<Function> applicationFunctions =
        new LambdaFunctionProvider(cache).getApplicationFunctions(applicationName);

    assertEquals(1, applicationFunctions.size());
    verify(cache, times(1)).get(APPLICATIONS.ns, appKey);
    verify(cache, times(1)).get(LAMBDA_FUNCTIONS.ns, functionKey);
  }

  @Test
  void getApplicationFunctionsWithoutApp() {
    String applicationName = "lambdaTest";
    String appKey =
        com.netflix.spinnaker.clouddriver.aws.data.Keys.getApplicationKey(applicationName);
    String functionKey = "functionKey";

    Cache cache = mock(Cache.class);
    when(cache.get(APPLICATIONS.ns, appKey)).thenReturn(null);

    when(cache.getAll(LAMBDA_FUNCTIONS.ns))
        .thenReturn(
            List.of(
                new DefaultCacheData(
                    appKey,
                    ImmutableMap.of("functionName", applicationName + "-" + functionKey),
                    ImmutableMap.of(LAMBDA_FUNCTIONS.ns, List.of(functionKey)))));

    Set<Function> applicationFunctions =
        new LambdaFunctionProvider(cache).getApplicationFunctions(applicationName);

    assertEquals(1, applicationFunctions.size());
    verify(cache, times(1)).get(APPLICATIONS.ns, appKey);
    verify(cache, times(1)).getAll(LAMBDA_FUNCTIONS.ns);
    verify(cache, times(0)).get(LAMBDA_FUNCTIONS.ns, functionKey);
  }
}

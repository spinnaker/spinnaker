/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.kork.web.interceptors;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spectator.api.Registry;
import com.netflix.spectator.micrometer.MicrometerRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

public class MetricsInterceptorMicrometerTest {

  HttpServletResponse RESPONSE = new MockHttpServletResponse();
  HandlerMethod HANDLER = handlerMethod(new TestController(), "execute");

  Collection<String> pathVariables = Arrays.asList("path-var-1", "path-var-2");
  Collection<String> queryParams = Arrays.asList("param-1", "param-2");
  Collection<String> controllersToExclude = Arrays.asList("ErrorController");

  SimpleMeterRegistry simpleMeterRegistry = new SimpleMeterRegistry();
  Registry registry = new MicrometerRegistry(simpleMeterRegistry);
  MetricsInterceptor interceptor =
      new MetricsInterceptor(
          registry, "controller.invocations", pathVariables, queryParams, controllersToExclude);

  @Test
  public void allPublishedMetricsHaveTheSameSetOfTagsAndCanBeRegisteredInMicrometer()
      throws Exception {
    MockHttpServletRequest request1 = new MockHttpServletRequest();
    request1.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Collections.emptyMap());

    MockHttpServletRequest request2 = new MockHttpServletRequest();
    request2.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        Collections.singletonMap("path-var-1", "path-val-1"));
    request2.setParameter("param-1", "val-1");

    interceptCall(interceptor, request1, RESPONSE, HANDLER, null);
    interceptCall(interceptor, request1, RESPONSE, HANDLER, new RuntimeException());
    interceptCall(interceptor, request2, RESPONSE, HANDLER, null);
    interceptCall(interceptor, request2, RESPONSE, HANDLER, new IllegalArgumentException());

    Search actual = simpleMeterRegistry.find("controller.invocations");
    assertThat(getAllTagsAndRemovePercentileTag(actual))
        .hasSize(4)
        .containsOnly(
            Arrays.asList(
                Tag.of("cause", "IllegalArgumentException"),
                Tag.of("controller", "TestController"),
                Tag.of("criticality", "unknown"),
                Tag.of("method", "execute"),
                Tag.of("param-1", "val-1"),
                Tag.of("param-2", "None"),
                Tag.of("path-var-1", "path-val-1"),
                Tag.of("path-var-2", "None"),
                Tag.of("statistic", "percentile"),
                Tag.of("status", "5xx"),
                Tag.of("statusCode", "500"),
                Tag.of("success", "false")),
            Arrays.asList(
                Tag.of("cause", "RuntimeException"),
                Tag.of("controller", "TestController"),
                Tag.of("criticality", "unknown"),
                Tag.of("method", "execute"),
                Tag.of("param-1", "None"),
                Tag.of("param-2", "None"),
                Tag.of("path-var-1", "None"),
                Tag.of("path-var-2", "None"),
                Tag.of("statistic", "percentile"),
                Tag.of("status", "5xx"),
                Tag.of("statusCode", "500"),
                Tag.of("success", "false")),
            Arrays.asList(
                Tag.of("cause", "None"),
                Tag.of("controller", "TestController"),
                Tag.of("criticality", "unknown"),
                Tag.of("method", "execute"),
                Tag.of("param-1", "val-1"),
                Tag.of("param-2", "None"),
                Tag.of("path-var-1", "path-val-1"),
                Tag.of("path-var-2", "None"),
                Tag.of("statistic", "percentile"),
                Tag.of("status", "2xx"),
                Tag.of("statusCode", "200"),
                Tag.of("success", "true")),
            Arrays.asList(
                Tag.of("cause", "None"),
                Tag.of("controller", "TestController"),
                Tag.of("criticality", "unknown"),
                Tag.of("method", "execute"),
                Tag.of("param-1", "None"),
                Tag.of("param-2", "None"),
                Tag.of("path-var-1", "None"),
                Tag.of("path-var-2", "None"),
                Tag.of("statistic", "percentile"),
                Tag.of("status", "2xx"),
                Tag.of("statusCode", "200"),
                Tag.of("success", "true")));
  }

  private Stream<List<Tag>> getAllTagsAndRemovePercentileTag(Search actual) {
    return actual.counters().stream()
        .map(c -> c.getId().getTags())
        .map(
            tags ->
                tags.stream()
                    .filter(tag -> !tag.getKey().equalsIgnoreCase("percentile"))
                    .collect(Collectors.toList()));
  }

  private void interceptCall(
      MetricsInterceptor interceptor,
      HttpServletRequest request,
      HttpServletResponse response,
      HandlerMethod handler,
      Exception exception)
      throws Exception {
    interceptor.preHandle(request, response, handler);

    interceptor.afterCompletion(request, response, handler, exception);
  }

  private HandlerMethod handlerMethod(TestController bean, String method) {
    try {
      return new HandlerMethod(bean, method);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private static class TestController {
    public void execute() {}
  }
}

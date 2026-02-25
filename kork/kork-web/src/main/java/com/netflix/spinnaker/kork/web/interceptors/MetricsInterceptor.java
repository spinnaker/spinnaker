/*
 * Copyright 2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.kork.web.interceptors;

import static java.lang.String.format;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileDistributionSummary;
import com.netflix.spectator.api.histogram.PercentileTimer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * An interceptor that logs Controller metrics to an underlying {@link
 * com.netflix.spectator.api.Registry}.
 *
 * <p>A `timer` will be created for each request with the following tags:
 *
 * <p>- controller name - controller method - status (2xx, 4xx, 5xx, etc.) - statusCode (200, 404,
 * 500, etc.) - success (true/false depending on whether the request resulted in an exception) -
 * cause (if success == false, the name of the raised exception)
 */
public class MetricsInterceptor implements HandlerInterceptor {
  static final String TIMER_ATTRIBUTE = "Metrics_startTime";

  private final Registry registry;
  private final String metricName;
  private final String contentLengthMetricName;
  private final Set<String> pathVariablesToTag = new HashSet<String>();
  private final Set<String> queryParamsToTag = new HashSet<String>();
  private final Set<String> controllersToExclude = new HashSet<String>();

  /**
   * @deprecated Instead use the other constructor.
   */
  @Deprecated
  public MetricsInterceptor(
      Registry registry,
      String metricName,
      Collection<String> pathVariablesToTag,
      Collection<String> controllersToExclude) {
    this(registry, metricName, pathVariablesToTag, null, controllersToExclude);
  }

  /**
   * @param registry Underlying metrics registry
   * @param metricName Metric name
   * @param pathVariablesToTag Variables from the request uri that should be added as metric tags
   * @param queryParamsToTag Request parameters that should be added as metric tags
   * @param controllersToExclude Controller names that should be excluded from metrics
   */
  public MetricsInterceptor(
      Registry registry,
      String metricName,
      Collection<String> pathVariablesToTag,
      Collection<String> queryParamsToTag,
      Collection<String> controllersToExclude) {
    this.registry = registry;
    this.metricName = metricName;
    this.contentLengthMetricName = format("%s.contentLength", metricName);
    if (pathVariablesToTag != null) {
      this.pathVariablesToTag.addAll(pathVariablesToTag);
    }
    if (queryParamsToTag != null) {
      this.queryParamsToTag.addAll(queryParamsToTag);
    }
    if (controllersToExclude != null) {
      this.controllersToExclude.addAll(controllersToExclude);
    }
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    request.setAttribute(TIMER_ATTRIBUTE, getNanoTime());
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
      throws Exception {
    if (handler instanceof HandlerMethod) {
      HandlerMethod handlerMethod = (HandlerMethod) handler;

      String controller = handlerMethod.getMethod().getDeclaringClass().getSimpleName();
      if (controllersToExclude.contains(controller)) {
        return;
      }

      Integer status = response.getStatus();
      if (ex != null) {
        // propagated exceptions should get tracked as '500' regardless of response status
        status = 500;
      }

      Id id =
          registry
              .createId(metricName)
              .withTag("controller", controller)
              .withTag("method", handlerMethod.getMethod().getName())
              .withTag("status", status.toString().charAt(0) + "xx")
              .withTag("statusCode", status.toString())
              .withTag("criticality", metricCriticality(handlerMethod));

      Map variables = (Map) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
      for (String pathVariable : pathVariablesToTag) {
        if (variables.containsKey(pathVariable)) {
          id = id.withTag(pathVariable, variables.get(pathVariable).toString());
        } else {
          id = id.withTag(pathVariable, "None");
        }
      }

      for (String queryParamName : queryParamsToTag) {
        String parameter = request.getParameter(queryParamName);
        if (parameter != null) {
          id = id.withTag(queryParamName, parameter);
        } else {
          id = id.withTag(queryParamName, "None");
        }
      }

      if (ex != null) {
        id = id.withTag("success", "false").withTag("cause", ex.getClass().getSimpleName());
      } else {
        id = id.withTag("success", "true").withTag("cause", "None");
      }

      PercentileTimer.get(registry, id)
          .record(
              getNanoTime() - ((Long) request.getAttribute(TIMER_ATTRIBUTE)), TimeUnit.NANOSECONDS);

      PercentileDistributionSummary.get(
              registry, registry.createId(contentLengthMetricName).withTags(id.tags()))
          .record(request.getContentLengthLong());
    }
  }

  private String metricCriticality(HandlerMethod handlerMethod) {
    if (handlerMethod.hasMethodAnnotation(Criticality.class)) {
      Criticality criticality = handlerMethod.getMethodAnnotation(Criticality.class);
      return criticality.value();
    } else if (handlerMethod
        .getMethod()
        .getDeclaringClass()
        .isAnnotationPresent(Criticality.class)) {
      Criticality criticality =
          handlerMethod.getMethod().getDeclaringClass().getAnnotation(Criticality.class);
      return criticality.value();
    }
    return Criticality.Value.UNKNOWN;
  }

  protected Long getNanoTime() {
    return System.nanoTime();
  }
}

package com.netflix.spinnaker.okhttp;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.OkHttpMetricsInterceptorProperties;
import com.netflix.spinnaker.kork.common.Header;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

/**
 * {@code MetricsInterceptor} is an encapsulation of common interception logic relevant to both
 * okhttp and okhttp3.
 *
 * <p>It is implemented as a single class with the expectation that legacy okhttp usage is on the
 * way out and the interceptor logic will not be necessary long term.
 */
class MetricsInterceptor {
  private final Provider<Registry> registry;
  private final OkHttpMetricsInterceptorProperties okHttpMetricsInterceptorProperties;
  private final Logger log;

  MetricsInterceptor(
      Provider<Registry> registry,
      OkHttpMetricsInterceptorProperties okHttpMetricsInterceptorProperties) {
    this.registry = registry;
    this.okHttpMetricsInterceptorProperties = okHttpMetricsInterceptorProperties;
    this.log = LoggerFactory.getLogger(getClass());
  }

  protected final Object doIntercept(Object chainObject) throws IOException {
    long start = System.nanoTime();
    boolean wasSuccessful = false;
    int statusCode = -1;

    Interceptor.Chain chain =
        (chainObject instanceof Interceptor.Chain) ? (Interceptor.Chain) chainObject : null;
    okhttp3.Interceptor.Chain chain3 =
        (chainObject instanceof okhttp3.Interceptor.Chain)
            ? (okhttp3.Interceptor.Chain) chainObject
            : null;

    Request request = (chain != null) ? chain.request() : null;
    okhttp3.Request request3 = (chain3 != null) ? chain3.request() : null;

    List<String> missingHeaders = new ArrayList<>();
    String method = null;
    URL url = null;

    try {

      Object response;

      if (chain != null) {
        method = request.method();
        url = request.url();
        response = chain.proceed(request);
        statusCode = ((Response) response).code();
      } else {
        method = request3.method();
        url = request3.url().url();
        response = chain3.proceed(request3);
        statusCode = ((okhttp3.Response) response).code();
      }

      if (checkForHeaders(url.toString())) {
        for (Header header : Header.values()) {
          String headerValue =
              (request != null)
                  ? request.header(header.getHeader())
                  : request3.header(header.getHeader());

          if (header.isRequired() && StringUtils.isEmpty(headerValue)) {
            missingHeaders.add(header.getHeader());
          }
        }
      }

      wasSuccessful = true;
      return response;
    } finally {
      boolean missingAuthHeaders = missingHeaders.size() > 0;

      if (missingAuthHeaders) {
        List<String> stack =
            Arrays.stream(Thread.currentThread().getStackTrace())
                .map(StackTraceElement::toString)
                .filter(x -> x.contains("com.netflix.spinnaker"))
                .collect(Collectors.toList());

        String stackTrace = String.join("\n\tat ", stack);
        log.warn(
            String.format(
                "Request %s:%s is missing %s authentication headers and will be treated as anonymous.\nRequest from: %s",
                method, url, missingHeaders, stackTrace));
      }

      recordTimer(
          registry.get(),
          url,
          System.nanoTime() - start,
          statusCode,
          wasSuccessful,
          !missingAuthHeaders);
    }
  }

  private boolean checkForHeaders(String url) {
    String xSpinAnonymous = MDC.get(Header.XSpinnakerAnonymous);
    Pattern endPointPatternForHeaderCheck =
        okHttpMetricsInterceptorProperties.getEndPointPatternForHeaderCheck();
    return xSpinAnonymous == null
        && !okHttpMetricsInterceptorProperties.isSkipHeaderCheck()
        && (endPointPatternForHeaderCheck == null
            || endPointPatternForHeaderCheck.matcher(url).matches());
  }

  private static void recordTimer(
      Registry registry,
      URL requestUrl,
      Long durationNs,
      int statusCode,
      boolean wasSuccessful,
      boolean hasAuthHeaders) {
    registry
        .timer(
            registry
                .createId("okhttp.requests")
                .withTag("requestHost", requestUrl.getHost())
                .withTag("statusCode", String.valueOf(statusCode))
                .withTag("status", bucket(statusCode))
                .withTag("success", wasSuccessful)
                .withTag("authenticated", hasAuthHeaders))
        .record(durationNs, TimeUnit.NANOSECONDS);
  }

  private static String bucket(int statusCode) {
    if (statusCode < 0) {
      return "Unknown";
    }

    return Integer.toString(statusCode).charAt(0) + "xx";
  }
}

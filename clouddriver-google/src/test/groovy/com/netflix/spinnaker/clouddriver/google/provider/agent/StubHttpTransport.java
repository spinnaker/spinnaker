/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.io.DefaultHttpRequestParser;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;

/**
 * A stub {@link HttpTransport} for Google API clients that generates user-supplied responses for
 * requests.
 *
 * <p>A key feature of this class (and, in fact, its primary raison d'être) is that it can be easily
 * configured to handle <a href="https://cloud.google.com/compute/docs/api/how-tos/batch">batch
 * requests</a>.
 */
final class StubHttpTransport extends HttpTransport {

  // An ordered map of request handlers. Each request will be passed through the predicates in the
  // keys. The first one that matches will call the function to generate a response.
  private final Map<
          Predicate<StubHttpTransportRequest>,
          Function<? super StubHttpTransportRequest, MockLowLevelHttpResponse>>
      responses = new LinkedHashMap<>();

  StubHttpTransport addBatchRequestHandlerForPath(Pattern pathPattern) {
    return addResponseGenerator(methodAndPath("POST", pathPattern), this::processBatchRequest);
  }

  StubHttpTransport addGetResponse(
      Pattern pathPattern,
      Function<? super StubHttpTransportRequest, MockLowLevelHttpResponse> responseGenerator) {

    return addResponseGenerator(methodAndPath("GET", pathPattern), responseGenerator);
  }

  private StubHttpTransport addResponseGenerator(
      Predicate<StubHttpTransportRequest> requestPredicate,
      Function<? super StubHttpTransportRequest, MockLowLevelHttpResponse> responseGenerator) {

    responses.put(requestPredicate, responseGenerator);
    return this;
  }

  @Override
  protected LowLevelHttpRequest buildRequest(String method, String urlString) {
    return new StubHttpTransportRequest(method, urlString);
  }

  // This class only exists because MockLowLevelHttpResponse doesn't store the HTTP method, sadly.
  final class StubHttpTransportRequest extends MockLowLevelHttpRequest {

    private final String method;

    StubHttpTransportRequest(String method, String urlString) {
      super(urlString);
      this.method = method;
    }

    String getMethod() {
      return method;
    }

    @Override
    public MockLowLevelHttpResponse getResponse() {
      return responses.entrySet().stream()
          .filter(e -> e.getKey().test(this))
          .findFirst()
          .map(e -> e.getValue().apply(this))
          .orElseThrow(
              () ->
                  new UnsupportedOperationException(
                      String.format(
                          "No response configured for %s request with URL %s",
                          this.getMethod(), this.getUrl())));
    }

    @Override
    public LowLevelHttpResponse execute() {
      return getResponse();
    }
  }

  private static final Pattern REQUEST_BOUNDARY_PATTERN =
      Pattern.compile("; boundary=([-_'()+,./:=?a-zA-Z0-9]+)");
  private static final String RESPONSE_BOUNDARY = "__batch_boundary__";

  // Google APIs allow submitting multiple API requests in a single HTTP call. They do this by
  // POSTing to a special URL. The content is a multipart/mixed message where each part is a full
  // HTTP request, with status lines, headers, and body. The responses are similar.
  // See https://cloud.google.com/compute/docs/api/how-tos/batch
  private MockLowLevelHttpResponse processBatchRequest(MockLowLevelHttpRequest request) {
    MultipartEntityBuilder multipartResponse =
        MultipartEntityBuilder.create().setBoundary(RESPONSE_BOUNDARY);
    try {
      for (byte[] part : parts(request)) {
        HttpRequest httpRequest = parseRequest(part);

        MockLowLevelHttpRequest googleRequest =
            (MockLowLevelHttpRequest)
                buildRequest(
                    httpRequest.getRequestLine().getMethod(),
                    httpRequest.getRequestLine().getUri());
        for (Header header : httpRequest.getAllHeaders()) {
          googleRequest.addHeader(header.getName(), header.getValue());
        }

        LowLevelHttpResponse response = googleRequest.execute();
        addResponse(multipartResponse, response);
      }

      return new MockLowLevelHttpResponse()
          .setContentType("multipart/mixed; boundary=" + RESPONSE_BOUNDARY)
          .setStatusCode(200)
          .setContent(new String(ByteStreams.toByteArray(multipartResponse.build().getContent())));
    } catch (IOException | HttpException e) {
      throw new IllegalStateException(e);
    }
  }

  private Iterable<byte[]> parts(MockLowLevelHttpRequest request) throws IOException {

    String contentType = request.getContentType();
    Matcher boundaryMatcher = REQUEST_BOUNDARY_PATTERN.matcher(contentType);
    if (!boundaryMatcher.find()) {
      throw new IllegalStateException("Couldn't find boundary in " + contentType);
    }
    String boundary = boundaryMatcher.group(1);
    MultipartStream multipartStream =
        new MultipartStream(
            new ByteArrayInputStream(request.getContentAsString().getBytes()),
            boundary.getBytes(),
            /* bufSize= */ 2048,
            /* pNotifier= */ null);
    boolean foundData = multipartStream.skipPreamble();
    if (!foundData) {
      return ImmutableList.of();
    }

    return () ->
        new AbstractIterator<byte[]>() {
          private boolean needsBoundaryRead = false;

          @Override
          protected byte[] computeNext() {
            try {
              if (needsBoundaryRead && !multipartStream.readBoundary()) {
                return endOfData();
              } else {
                needsBoundaryRead = true;
              }
              multipartStream.readHeaders();
              ByteArrayOutputStream output = new ByteArrayOutputStream();
              multipartStream.readBodyData(output);
              return output.toByteArray();
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          }
        };
  }

  private HttpRequest parseRequest(byte[] part) throws IOException, HttpException {
    ByteArrayInputStream input = new ByteArrayInputStream(part);
    SessionInputBufferImpl buffer =
        new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 10240);
    buffer.bind(input);
    DefaultHttpRequestParser requestParser = new DefaultHttpRequestParser(buffer);
    return requestParser.parse();
  }

  private void addResponse(MultipartEntityBuilder multipartResponse, LowLevelHttpResponse response)
      throws IOException {
    byte[] responseBytes = ByteStreams.toByteArray(response.getContent());
    multipartResponse.addTextBody(
        "part", "HTTP/1.1 " + response.getStatusLine() + "\n\n" + new String(responseBytes));
  }

  private static Predicate<StubHttpTransportRequest> methodAndPath(
      String method, Pattern pathPattern) {
    return request ->
        request.getMethod().equals(method)
            && pathPattern.matcher(urlPath(request.getUrl())).matches();
  }

  private static String urlPath(String url) {
    try {
      return new URL(url).getPath();
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }
}

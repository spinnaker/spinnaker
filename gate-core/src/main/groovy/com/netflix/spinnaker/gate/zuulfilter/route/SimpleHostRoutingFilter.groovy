/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.gate.zuulfilter.route

import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.Debug
import com.netflix.zuul.context.RequestContext
import com.netflix.zuul.util.HTTPRequestUtils
import groovy.transform.CompileStatic
import org.apache.http.Header
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.RedirectStrategy
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.config.Registry
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.HttpClientConnectionManager
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.entity.InputStreamEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicHttpRequest
import org.apache.http.protocol.HttpContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

import javax.servlet.http.HttpServletRequest
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream

@CompileStatic
@Component
class SimpleHostRoutingFilter extends ZuulFilter {

    public static final String CONTENT_ENCODING = "Content-Encoding"

    private static final Logger LOG = LoggerFactory.getLogger(SimpleHostRoutingFilter.class)

//TODO - hook up to environment properties instead of DynamicPropertyFactory
//    private static final Runnable CLIENT_LOADER = new Runnable() {
//        @Override
//        void run() {
//            loadClient()
//        }
//    }
//
//    private static final DynamicIntProperty SOCKET_TIMEOUT =
//            DynamicPropertyFactory.getInstance().getIntProperty(ZuulConstants.ZUUL_HOST_SOCKET_TIMEOUT_MILLIS, 10000)
//
//    private static final DynamicIntProperty CONNECTION_TIMEOUT =
//            DynamicPropertyFactory.getInstance().getIntProperty(ZuulConstants.ZUUL_HOST_CONNECT_TIMEOUT_MILLIS, 2000)
    private static final AtomicReference<Integer> SOCKET_TIMEOUT = new AtomicReference<>(10000)
    private static final AtomicReference<Integer> CONNECTION_TIMEOUT = new AtomicReference<>(2000)

    private static final AtomicReference<ClientAndConnectionManager> CLIENT = new AtomicReference<>(newClient())

    private static class ClientAndConnectionManager {
        final CloseableHttpClient httpClient
        final HttpClientConnectionManager httpClientConnectionManager

        ClientAndConnectionManager(CloseableHttpClient httpClient, HttpClientConnectionManager httpClientConnectionManager) {
            this.httpClient = httpClient
            this.httpClientConnectionManager = httpClientConnectionManager
        }
    }

    private static final Timer CONNECTION_MANAGER_TIMER = new Timer(true)

    // cleans expired connections at an interval
    static {
//        SOCKET_TIMEOUT.addCallback(CLIENT_LOADER)
//        CONNECTION_TIMEOUT.addCallback(CLIENT_LOADER)
        CONNECTION_MANAGER_TIMER.schedule(new TimerTask() {
            @Override
            void run() {
                try {
                    final ClientAndConnectionManager hc = CLIENT.get()
                    if (hc == null) return
                    hc.getHttpClientConnectionManager().closeExpiredConnections()
                } catch (Throwable t) {
                    LOG.error("error closing expired connections", t)
                }
            }
        }, 30000, 5000)
    }

    public SimpleHostRoutingFilter() {}

    private static final HttpClientConnectionManager newConnectionManager() {
        Registry<ConnectionSocketFactory> schemeRegistry = RegistryBuilder.create()
                .register("http", (ConnectionSocketFactory) PlainConnectionSocketFactory.getSocketFactory())
                .build()

        HttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(schemeRegistry)
        cm.setMaxTotal(Integer.parseInt(System.getProperty("zuul.max.host.connections", "200")))
        cm.setDefaultMaxPerRoute(Integer.parseInt(System.getProperty("zuul.max.host.connections", "20")))
        return cm
    }

    @Override
    String filterType() {
        return 'route'
    }

    @Override
    int filterOrder() {
        return 100
    }

    boolean shouldFilter() {
        def context = RequestContext.getCurrentContext()
        context.getRouteHost() && context.sendZuulResponse()
    }

    private static final void loadClient() {
        final ClientAndConnectionManager oldClient = CLIENT.getAndSet(newClient())
        if (oldClient != null) {
            CONNECTION_MANAGER_TIMER.schedule(new TimerTask() {
                @Override
                void run() {
                    try {
                        oldClient.httpClient.close()
                    } catch (Throwable t) {
                        LOG.error("error shutting down old connection manager", t)
                    }
                }
            }, 30000)
        }

    }

    private static final ClientAndConnectionManager newClient() {
        final HttpClientConnectionManager connectionManager = newConnectionManager()
        final CloseableHttpClient client = HttpClientBuilder.create()
            .setConnectionManager(connectionManager)
            .setRetryHandler(new DefaultHttpRequestRetryHandler(0, false))
            .setDefaultRequestConfig(RequestConfig.custom()
                .setSocketTimeout(SOCKET_TIMEOUT.get())
                .setConnectTimeout(CONNECTION_TIMEOUT.get())
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
            .build())
            .setRedirectStrategy(new RedirectStrategy() {
                @Override
                boolean isRedirected(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) {
                    return false
                }

                @Override
                HttpUriRequest getRedirect(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext httpContext) {
                    return null
                }
            }).build()
        new ClientAndConnectionManager(client, connectionManager)
    }

    Object run() {
        RequestContext requestContext = RequestContext.getCurrentContext()
        HttpServletRequest request = requestContext.getRequest()
        Header[] headers = buildZuulRequestHeaders(request)
        String verb = getVerb(request)
        InputStream requestEntity = getRequestBody(request)
        HttpClient httpClient = CLIENT.get().httpClient

        String uri = request.getRequestURI()
        if (requestContext.requestURI != null) {
            uri = requestContext.requestURI
        }

        try {
            HttpResponse response = forward(httpClient, verb, uri, request, headers, requestEntity)
            setResponse(response)
        } catch (Exception e) {
            throw e
        }
        return null
    }

    InputStream debug(String verb, String uri, HttpServletRequest request, Header[] headers, InputStream requestEntity) {

        if (Debug.debugRequest()) {

            Debug.addRequestDebug("ZUUL:: host=${RequestContext.getCurrentContext().getRouteHost()}")

            headers.each { Header it ->
                Debug.addRequestDebug("ZUUL::> ${it.name}  ${it.value}")
            }
            String query = request.queryString

            Debug.addRequestDebug("ZUUL:: > ${verb}  ${uri}?${query} HTTP/1.1")
            if (requestEntity != null) {
                requestEntity = debugRequestEntity(requestEntity)
            }

        }
        return requestEntity
    }

    InputStream debugRequestEntity(InputStream inputStream) {
        if (Debug.debugRequestHeadersOnly()) return inputStream
        if (inputStream == null) return null
        String entity = inputStream.getText()
        Debug.addRequestDebug("ZUUL::> ${entity}")
        return new ByteArrayInputStream(entity.bytes)
    }

    def HttpResponse forward(HttpClient httpClient, String verb, String uri, HttpServletRequest request, Header[] headers, InputStream requestEntity) {

        requestEntity = debug(verb, uri, request, headers, requestEntity)

        HttpHost httpHost

        httpHost = getHttpHost()

        HttpRequest httpRequest

        switch (verb) {
            case 'POST':
                httpRequest = new HttpPost(uri + getQueryString())
                InputStreamEntity entity = new InputStreamEntity(requestEntity, request.getContentLength())
                httpRequest.setEntity(entity)
                break
            case 'PUT':
                httpRequest = new HttpPut(uri + getQueryString())
                InputStreamEntity entity = new InputStreamEntity(requestEntity, request.getContentLength())
                httpRequest.setEntity(entity)
                break
            default:
                httpRequest = new BasicHttpRequest(verb, uri + getQueryString())
        }

        httpRequest.setHeaders(headers)
        HttpResponse zuulResponse = forwardRequest(httpClient, httpHost, httpRequest)
        return zuulResponse
    }

    HttpResponse forwardRequest(HttpClient httpClient, HttpHost httpHost, HttpRequest httpRequest) {
        return httpClient.execute(httpHost, httpRequest)
    }

    String getQueryString() {
        HttpServletRequest request = RequestContext.getCurrentContext().getRequest()
        String query = request.getQueryString()
        return (query != null) ? "?${query}" : ""
    }

    HttpHost getHttpHost() {
        HttpHost httpHost
        URL host = RequestContext.getCurrentContext().getRouteHost()

        httpHost = new HttpHost(host.getHost(), host.getPort(), host.getProtocol())

        return httpHost
    }


    InputStream getRequestBody(HttpServletRequest request) {
        try {
            return request.getInputStream()
        } catch (IOException ignored) {
            //no requestBody is ok.
        }
        return null
    }

    boolean isValidHeader(String name) {
        if (name.toLowerCase().contains("content-length")) return false
        if (!RequestContext.getCurrentContext().responseGZipped) {
            if (name.toLowerCase().contains("accept-encoding")) return false
        }
        return true
    }

    def Header[] buildZuulRequestHeaders(HttpServletRequest request) {

        def headers = new ArrayList<BasicHeader>()
        Enumeration headerNames = request.getHeaderNames()
        while (headerNames.hasMoreElements()) {
            String name = (String) headerNames.nextElement()
            String value = request.getHeader(name)
            if (isValidHeader(name)) headers.add(new BasicHeader(name, value))
        }

        def requestContext = RequestContext.getCurrentContext()
        Map<String, String> zuulRequestHeaders = requestContext.getZuulRequestHeaders()

        zuulRequestHeaders.keySet().each {
            String name = it.toLowerCase()
            BasicHeader h = headers.find { BasicHeader he -> he.name == name }
            if (h != null) {
                headers.remove(h)
            }
            headers.add(new BasicHeader((String) it, (String) zuulRequestHeaders[it]))
        }

        if (requestContext.responseGZipped) {
            headers.add(new BasicHeader("accept-encoding", "deflate, gzip"))
        }
        return headers.toArray(new Header[headers.size()])
    }



    String getVerb(HttpServletRequest request) {
        String sMethod = request.getMethod()
        return sMethod.toUpperCase()
    }

    void setResponse(HttpResponse response) {
        RequestContext context = RequestContext.getCurrentContext()

        context.set("hostZuulResponse", response)
        context.setResponseStatusCode(response.getStatusLine().statusCode)
        context.responseDataStream = response?.entity?.content

        boolean isOriginResponseGzipped = false

        for (Header h : response.getHeaders(CONTENT_ENCODING)) {
            if (HTTPRequestUtils.getInstance().isGzipped(h.value)) {
                isOriginResponseGzipped = true
                break
            }
        }
        context.setResponseGZipped(isOriginResponseGzipped)

        if (Debug.debugRequest()) {
            response.getAllHeaders()?.each { Header header ->
                if (isValidHeader(header)) {
                    context.addZuulResponseHeader(header.name, header.value)
                    Debug.addRequestDebug("ORIGIN_RESPONSE:: < ${header.name}, ${header.value}")
                }
            }

            if (context.responseDataStream) {
                byte[] origBytes = context.getResponseDataStream().bytes
                ByteArrayInputStream byteStream = new ByteArrayInputStream(origBytes)
                InputStream inputStream = byteStream
                if (context.responseGZipped) {
                    inputStream = new GZIPInputStream(byteStream)
                }


                context.setResponseDataStream(inputStream)
            }

        } else {
            response.getAllHeaders()?.each { Header header ->
                context.addOriginResponseHeader(header.name, header.value)

                if (header.name.equalsIgnoreCase("content-length"))
                    context.setOriginContentLength(header.value)

                if (isValidHeader(header)) {
                    context.addZuulResponseHeader(header.name, header.value)
                }
            }
        }
    }

    boolean isValidHeader(Header header) {
        switch (header.name.toLowerCase()) {
            case "connection":
            case "content-length":
            case "content-encoding":
            case "server":
            case "transfer-encoding":
                return false
            default:
                return true
        }
    }

}

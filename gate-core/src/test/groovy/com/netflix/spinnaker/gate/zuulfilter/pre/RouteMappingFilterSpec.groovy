/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.zuulfilter.pre

import com.netflix.spinnaker.gate.config.RouteMapping
import com.netflix.spinnaker.gate.config.RoutesConfig
import com.netflix.zuul.context.RequestContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import javax.servlet.http.HttpServletRequest

class RouteMappingFilterSpec extends Specification {

    @Subject
    RouteMappingFilter routeMappingFilter

    void setup() {
        RoutesConfig cfg = new RoutesConfig(routeMappings: [
                new RouteMapping(contextPath: '/foo', routeHost: 'http://foo.com'.toURL()),
                new RouteMapping(contextPath: 'withRoutePrefix', routeHost: 'http://foo.com/prefix'.toURL()),
                new RouteMapping(contextPath: '/foo/willNeverMatch', routeHost: 'http://foo.com/never'.toURL()),
                new RouteMapping(contextPath: '/bar/willMatch', routeHost: 'http://bar.com/match'.toURL()),
                new RouteMapping(contextPath: '/bar', routeHost: 'http://bar.com/others'.toURL())])
        routeMappingFilter = new RouteMappingFilter(apiPrefix: '/api', apiRouteConfig: cfg)
        routeMappingFilter.initRegex()
    }

    @Unroll
    void 'should resolve #requestUri to #expectedUrl'(String requestUri, String expectedUrl) {
        expect:
        routeMappingFilter.findRouteMapping(requestUri).orNull() == expectedUrl?.toURL()

        where:
        requestUri                       | expectedUrl
        '/wrongPrefix'                   | null
        '/api'                           | null
        '/api/foo'                       | 'http://foo.com/'
        '/api/foo/'                      | 'http://foo.com/'
        '/api/foo/more'                  | 'http://foo.com/more'
        '/api/foo/willNeverMatch'        | 'http://foo.com/willNeverMatch'
        '/api/withRoutePrefix'           | 'http://foo.com/prefix/'
        '/api/withRoutePrefixButNoSlash' | null
        '/api/withRoutePrefix/more'      | 'http://foo.com/prefix/more'
        '/api/bar/willMatch/stuff'       | 'http://bar.com/match/stuff'
        '/api/bar/willMatchStuff'        | 'http://bar.com/others/willMatchStuff'
    }

    void 'should not modify requestContext when no rule present'() {
        setup:
        RequestContext testContext = new RequestContext()
        HttpServletRequest httpReq = Mock(HttpServletRequest)
        testContext.setRequest(httpReq)
        RequestContext.testSetCurrentContext(testContext)

        when:
        routeMappingFilter.run()

        then:
        1 * httpReq.getRequestURI() >> '/api/unmapped'
        testContext.getZuulRequestHeaders().isEmpty()
        testContext.getRouteHost() == null
        testContext.requestURI == null
        0 * _

        cleanup:
        RequestContext.testSetCurrentContext(null)
    }

    void 'should set requestContext on match'() {
        setup:
        RequestContext testContext = new RequestContext()
        HttpServletRequest httpReq = Mock(HttpServletRequest)
        testContext.setRequest(httpReq)
        RequestContext.testSetCurrentContext(testContext)

        when:
        routeMappingFilter.run()

        then:
        1 * httpReq.getRequestURI() >> '/api/foo/some/awesome/request'
        1 * httpReq.getRequestURL() >> new StringBuffer('http://spinnakerproxy.com/api/foo/some/awesome/request')
        testContext.getZuulRequestHeaders().get('host') == 'foo.com'
        testContext.getZuulRequestHeaders().get('via') == '1.1 spinnakerproxy.com'
        testContext.getRouteHost() == 'http://foo.com/some/awesome/request'.toURL()
        testContext.requestURI == '/some/awesome/request'
        0 * _

        cleanup:
        RequestContext.testSetCurrentContext(null)
    }
}

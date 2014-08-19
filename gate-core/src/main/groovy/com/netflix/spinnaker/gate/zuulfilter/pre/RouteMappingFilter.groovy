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

import com.google.common.base.Optional
import com.netflix.spinnaker.gate.config.RoutesConfig
import com.netflix.spinnaker.gate.config.RouteMapping
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.RequestContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.regex.Pattern

@Component
class RouteMappingFilter extends ZuulFilter {

    @Autowired
    RoutesConfig apiRouteConfig

    @Autowired
    String apiPrefix

    private LinkedHashMap<Pattern, URI> mappingPatterns = new LinkedHashMap<>()

    @PostConstruct
    public void initRegex() {
        def baseUri = apiPrefix.endsWith('/') ? apiPrefix : apiPrefix + '/'
        for (RouteMapping routeMapping in apiRouteConfig.routeMappings) {
            def contextPath = (baseUri + routeMapping.contextPath).toURI().normalize().path
            if (contextPath.endsWith('/')) {
                contextPath = contextPath.substring(0, contextPath.length() - 1)
            }
            def pattern = Pattern.compile(/^${Pattern.quote(contextPath)}(\/.*)?$/)

            def origUri = routeMapping.routeHost.toURI()
            def origPath = origUri.path.endsWith('/') ? origUri.path : origUri.path + '/'
            mappingPatterns[pattern] = origUri.resolve(origPath).normalize()
        }
    }

    @Override
    String filterType() {
        'pre'
    }

    @Override
    int filterOrder() {
        return 99
    }

    @Override
    boolean shouldFilter() {
        return true
    }

    @Override
    Object run() {
        def requestContext = RequestContext.getCurrentContext()
        for (URL url : findRouteMapping(requestContext.getRequest().requestURI).asSet()) {
            requestContext.setRouteHost(url)
            requestContext.requestURI = url.path
            requestContext.addZuulRequestHeader('Host', url.host)
            requestContext.addZuulRequestHeader('Via', '1.1 ' + requestContext.getRequest().getRequestURL().toURL().getHost())
        }
        return null
    }

    Optional<URL> findRouteMapping(String requestURI) {
        Optional.fromNullable(mappingPatterns.findResult { Pattern pattern, URI routeMapping ->
            def match = pattern.matcher(requestURI)
            if (match.matches()) {
                routeMapping.resolve('.' + (match.group(1) ?: '')).normalize().toURL()
            } else {
                null
            }
        })
    }
}

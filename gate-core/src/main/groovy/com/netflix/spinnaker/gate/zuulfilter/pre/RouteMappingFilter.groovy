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

    private Pattern contextPathPattern

    @Autowired
    RoutesConfig apiRouteConfig

    @Autowired
    String apiPrefix

    @PostConstruct
    public void initRegex() {
        contextPathPattern = Pattern.compile(/${Pattern.quote(apiPrefix)}([^\/]+)(\/.*)?$/)
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
        def match = contextPathPattern.matcher(requestContext.getRequest().getRequestURI())
        if (match.matches()) {
            def contextPath = match.group(1)
            RouteMapping routeMapping = apiRouteConfig.routeMappings.find { it.contextPath == contextPath }
            if (routeMapping) {
                requestContext.setRouteHost(routeMapping.routeHost)
                requestContext.requestURI = match.group(2) ?: '/'
                requestContext.addZuulRequestHeader('Location', routeMapping.routeHost.host)
            }
        }
        return null
    }
}

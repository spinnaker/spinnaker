/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.fiat.shared

import com.netflix.spinnaker.fiat.model.Authorization
import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.model.resources.Application
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import spock.lang.Subject
import org.springframework.security.access.AccessDeniedException

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class FiatAccessDeniedExceptionHandlerSpec extends FiatSharedSpecification {
    @Subject
    def fiatAccessDeniedExceptionHandler = new FiatAccessDeniedExceptionHandler()

    def request = Mock(HttpServletRequest)
    def response = Mock(HttpServletResponse)

    FiatPermissionEvaluator evaluator = new FiatPermissionEvaluator(
            registry,
            fiatService,
            buildConfigurationProperties(),
            fiatStatus,
            FiatPermissionEvaluator.RetryHandler.NOOP
    )

    def "when access denied exception is handled, a descriptive error message is sent in response"() {
        setup:
        def authentication = new PreAuthenticatedAuthenticationToken("testUser", null, [])
        String resource = "service"

        UserPermission.View upv = new UserPermission.View()
        upv.setApplications([new Application.View().setName(resource)
                                     .setAuthorizations([Authorization.READ] as Set)] as Set)
        fiatService.getUserPermission("testUser") >> upv

        when:
        evaluator.hasPermission(authentication, resource, 'APPLICATION', 'WRITE')

        and:
        fiatAccessDeniedExceptionHandler.handleAccessDeniedException(new AccessDeniedException("Forbidden"), response, request)

        then:
        1 * response.sendError(403, "Access denied to application service - required authorization: WRITE")
    }
}

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
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount
import com.netflix.spinnaker.kork.api.exceptions.ExceptionDetails
import com.netflix.spinnaker.kork.api.exceptions.ExceptionMessage
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator
import org.jetbrains.annotations.Nullable
import org.springframework.beans.BeansException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import spock.lang.Shared
import spock.lang.Subject
import org.springframework.security.access.AccessDeniedException
import spock.lang.Unroll

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class FiatAccessDeniedExceptionHandlerSpec extends FiatSharedSpecification {

    String additionalInformation = "Additional information"

    ExceptionMessageDecorator exceptionMessageDecorator = new ExceptionMessageDecorator(new ObjectProvider<List<ExceptionMessage>>() {
        @Override
        List<ExceptionMessage> getObject(Object... args) throws BeansException {
            return null
        }

        @Override
        List<ExceptionMessage> getIfAvailable() throws BeansException {
            return [new ExceptionMessage() {

                @Override
                boolean supports(Class<? extends Throwable> throwable) {
                    return throwable == AccessDeniedException.class
                }

                @Override
                String message(Throwable throwable, @Nullable ExceptionDetails exceptionDetails) {
                    return additionalInformation
                }
            }]
        }

        @Override
        List<ExceptionMessage> getIfUnique() throws BeansException {
            return null
        }

        @Override
        List<ExceptionMessage> getObject() throws BeansException {
            return null
        }
    })

    @Subject
    def fiatAccessDeniedExceptionHandler = new FiatAccessDeniedExceptionHandler(exceptionMessageDecorator)

    def request = Mock(HttpServletRequest)
    def response = Mock(HttpServletResponse)

    @Shared
    def authentication = new PreAuthenticatedAuthenticationToken("testUser", null, [])

    FiatPermissionEvaluator evaluator = new FiatPermissionEvaluator(
            registry,
            fiatService,
            buildConfigurationProperties(),
            fiatStatus,
            FiatPermissionEvaluator.RetryHandler.NOOP
    )

    @Unroll
    def "when access denied exception is handled accessing application, an appropriate error message is sent in response"() {
        setup:
        String resource = "service"
        UserPermission.View upv = new UserPermission.View()
        upv.setApplications([new Application.View().setName(resource)
                                     .setAuthorizations([userAuthorizationType] as Set)] as Set)
        fiatService.getUserPermission("testUser") >> upv

        when:
        evaluator.hasPermission(authentication, resource, 'APPLICATION', authorizationTypeRequired)

        and:
        fiatAccessDeniedExceptionHandler.handleAccessDeniedException(new AccessDeniedException("Forbidden"), response, request)

        then:
        1 * response.sendError(403, "Access denied to application service - required authorization: " + authorizationTypeRequired + "\n" + additionalInformation)

        where:
        userAuthorizationType | authorizationTypeRequired
        Authorization.READ    | "WRITE"
        Authorization.WRITE   | "READ"
    }

    def "when access denied exception is handled accessing a service account, an appropriate error message is sent in response"() {
        setup:
        String resource = "readable"
        String svcAcct = "svcAcct"
        UserPermission.View upv = new UserPermission.View()
        upv.setApplications([new Application.View().setName(resource)
                                     .setAuthorizations([Authorization.READ] as Set)] as Set)
        upv.setServiceAccounts([new ServiceAccount.View().setName(svcAcct)
                                        .setMemberOf(["foo"])] as Set)
        fiatService.getUserPermission("testUser") >> upv

        when:
        evaluator.hasPermission(authentication, resource, 'SERVICE_ACCOUNT', 'WRITE')

        and:
        fiatAccessDeniedExceptionHandler.handleAccessDeniedException(new AccessDeniedException("Forbidden"), response, request)

        then:
        1 * response.sendError(403, "Access denied to service account readable" + "\n" + additionalInformation)
    }
}

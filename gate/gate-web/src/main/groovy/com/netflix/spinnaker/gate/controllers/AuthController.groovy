/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.gate.controllers

import com.netflix.spinnaker.gate.security.SpinnakerUser
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.gate.services.SessionService
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter;
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletResponse
import java.util.regex.Pattern

@Slf4j
@RestController
@RequestMapping("/auth")
class AuthController {

  static List LOGOUT_MESSAGES = [
      "Hasta la vista, baby.",
      "Frankly, my dear, I don't give a damn.",
      "For the Watch.",
      "Go ahead, make my day.",
      "Louis, I think this is the beginning of a beautiful friendship.",
      "Roads? Where we're going we don't need roads!",
      "Say hello to my little friend!",
      "I do wish we could chat longer, but... I'm having an old friend for dinner. Bye.",
      "Hodor. :(",
  ]
  private final Random r = new Random()
  private final URL deckBaseUrl
  private final Pattern redirectHostPattern

  @Autowired
  PermissionService permissionService

  SessionService sessionService

  @Autowired
  AuthController(@Value('${services.deck.base-url:}') URL deckBaseUrl,
                 @Value('${services.deck.redirect-host-pattern:#{null}}') String redirectHostPattern,
                 @Autowired SessionService sessionService) {
    this.deckBaseUrl = deckBaseUrl
    this.sessionService = sessionService

    if (redirectHostPattern) {
      this.redirectHostPattern = Pattern.compile(redirectHostPattern)
    }
  }

  @Operation(summary = "Get user")
  @RequestMapping(value = "/user", method = RequestMethod.GET)
  User user(@Parameter(hidden = true) @SpinnakerUser User user) {
    if (!user) {
      return user
    }

    def fiatRoles = permissionService.getRoles(user.username)?.collect{ it.name }
    if (fiatRoles) {
      user.roles = fiatRoles
    }
    return user
  }

  @Operation(summary = "Get service accounts", hidden=true)
  @RequestMapping(value = "/user/serviceAccounts", method = RequestMethod.GET)
  List<String> getServiceAccounts(@Parameter(hidden = true) @SpinnakerUser User user,
                                  @RequestParam(name = "application", required = false) String application) {

    String appName = Optional.ofNullable(application)
      .map({ s -> s.trim() })
      .filter({ s -> !s.isEmpty()})
      .orElse(null);


    if (appName == null) {
      return permissionService.getServiceAccounts(user)
    }

    return permissionService.getServiceAccountsForApplication(user, appName)
  }

  @Operation(summary = "Get logged out message")
  @RequestMapping(value = "/loggedOut", method = RequestMethod.GET)
  String loggedOut() {
    return LOGOUT_MESSAGES[r.nextInt(LOGOUT_MESSAGES.size())]
  }

  /**
   * On-demand endpoint to sync the user roles, in case
   * waiting for the periodic refresh won't work.
   */
  @Operation(summary = "Sync user roles")
  @RequestMapping(value = "/roles/sync", method = RequestMethod.POST)
  @PreAuthorize("@authController.isAdmin()")
  void sync() {
    permissionService.sync()
  }

  /**
   * On-demand endpoint to purge the session tokens cache
   */
  @Operation(summary = "Delete session cache")
  @RequestMapping(value = "/deleteSessionCache", method = RequestMethod.POST)
  @PreAuthorize("@authController.isAdmin()")
  void deleteSessionCache() {
    sessionService.deleteSpringSessions()
  }

  @Operation(summary = "Redirect to Deck")
  @RequestMapping(value = "/redirect", method = RequestMethod.GET)
  void redirect(HttpServletResponse response, @RequestParam String to) {
    validDeckRedirect(to) ?
        response.sendRedirect(to) :
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Requested redirect address not recognized.")
  }

  boolean validDeckRedirect(String to) {
    URL toURL
    try {
      toURL = new URL(to)
    } catch (MalformedURLException e) {
      log.warn("Malformed redirect URL {}", to, e)
      return false
    }

    log.debug([
      "validateDeckRedirect(${to})",
      "toUrl(host: ${toURL.host}, port: ${toURL.port})",
      "deckBaseUrl(host: ${deckBaseUrl.host}, port: ${deckBaseUrl.port})",
      "redirectHostPattern(${redirectHostPattern?.pattern()})"
      ].join(" - ")
    )

    if (redirectHostPattern) {
      def matcher = redirectHostPattern.matcher(toURL.host)
      return matcher.matches()
    }

    def toURLPort = (toURL.port == -1 && toURL.protocol == 'https') ? 443 : toURL.port
    def deckBaseUrlPort = (deckBaseUrl.port == -1 && deckBaseUrl.protocol == 'https') ? 443 : deckBaseUrl.port

    return toURL.host == deckBaseUrl.host &&
        toURLPort == deckBaseUrlPort
  }

  boolean isAdmin() {
    return permissionService.isAdmin(
        AuthenticatedRequest.getSpinnakerUser().orElse("anonymous")
    )
  }
}

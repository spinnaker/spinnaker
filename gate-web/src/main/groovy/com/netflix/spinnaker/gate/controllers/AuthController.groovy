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
import com.netflix.spinnaker.security.AuthenticatedRequest
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import io.swagger.annotations.ApiOperation
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import springfox.documentation.annotations.ApiIgnore

import javax.servlet.http.HttpServletResponse
import java.util.regex.Pattern

@Slf4j
@RestController
@RequestMapping("/auth")
class AuthController {

  static List LOGOUT_MESSAGES = [
      "Hasta la vista, baby.",
      "Frankly my dear, I don't give a damn.",
      "For the Watch.",
      "Go ahead, make my day.",
      "Louis, I think this is a start of a beautiful friendship!",
      "Roads? Where we're going we don't need roads!",
      "Say hello to my little friend!",
      "I wish we could chat longer, but I'm having an old friend for dinner. Bye!",
      "Hodor. :(",
  ]
  private final Random r = new Random()
  private final URL deckBaseUrl
  private final Pattern redirectHostPattern

  @Autowired
  PermissionService permissionService

  @Autowired
  AuthController(@Value('${services.deck.base-url:}') URL deckBaseUrl,
                 @Value('${services.deck.redirect-host-pattern:#{null}}') String redirectHostPattern) {
    this.deckBaseUrl = deckBaseUrl

    if (redirectHostPattern) {
      this.redirectHostPattern = Pattern.compile(redirectHostPattern)
    }
  }

  @ApiOperation(value = "Get user", response = User.class)
  @RequestMapping(value = "/user", method = RequestMethod.GET)
  User user(@ApiIgnore @SpinnakerUser User user) {
    if (!user) {
      return user
    }

    def fiatRoles = permissionService.getRoles(user.username)?.collect{ it.name }
    if (fiatRoles) {
      user.roles = fiatRoles
    }
    return user
  }

  @ApiOperation(value = "Get service accounts", response = List.class)
  @RequestMapping(value = "/user/serviceAccounts", method = RequestMethod.GET)
  List<String> getServiceAccounts(@ApiIgnore @SpinnakerUser User user,
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

  @ApiOperation(value = "Get logged out message", response = String.class)
  @RequestMapping(value = "/loggedOut", method = RequestMethod.GET)
  String loggedOut() {
    return LOGOUT_MESSAGES[r.nextInt(LOGOUT_MESSAGES.size())]
  }

  /**
   * On-demand endpoint to sync the user roles, in case
   * waiting for the periodic refresh won't work.
   */
  @ApiOperation(value = "Sync user roles")
  @RequestMapping(value = "/roles/sync", method = RequestMethod.POST)
  @PreAuthorize("@authController.isAdmin()")
  void sync() {
    permissionService.sync()
  }

  @ApiOperation(value = "Redirect to Deck")
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

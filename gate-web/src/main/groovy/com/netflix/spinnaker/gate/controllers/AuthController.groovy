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
import com.netflix.spinnaker.gate.security.rolesprovider.UserRolesSyncer
import com.netflix.spinnaker.gate.services.PermissionService
import com.netflix.spinnaker.security.User
import groovy.util.logging.Slf4j
import org.apache.commons.lang.exception.ExceptionUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletResponse

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
      "Hodor.",
  ]

  @Autowired
  PermissionService permissionService

  @Autowired(required = false)
  UserRolesSyncer userRolesSyncer

  @Value('${services.deck.baseUrl}')
  URL deckBaseUrl

  Random r = new Random()

  @RequestMapping("/user")
  User user(@SpinnakerUser User user) {
    user
  }

  @RequestMapping("/user/serviceAccounts")
  List<String> getServiceAccounts(@SpinnakerUser User user) {
    permissionService.getServiceAccounts(user)
  }

  @RequestMapping("/loggedOut")
  String loggedOut() {
    return LOGOUT_MESSAGES[r.nextInt(LOGOUT_MESSAGES.size()+1)]
  }

  /**
   * On-demand endpoint to sync the user roles, in case
   * waiting for the periodic refresh won't work.
   */
  @RequestMapping(value = "/roles/sync", method = RequestMethod.POST)
  void sync() {
    if (userRolesSyncer) {
      userRolesSyncer.sync()
    } else {
      permissionService.sync()
    }
  }

  @RequestMapping("/redirect")
  void redirect(HttpServletResponse response, @RequestParam String to) {
    validDeckRedirect(to) ?
        response.sendRedirect(to) :
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Requested redirect address not recognized.")
  }

  boolean validDeckRedirect(String to) {
    URL toURL
    try {
      toURL = new URL(to)
    } catch (MalformedURLException malEx) {
      log.warn "Malformed redirect URL: $to\n${ExceptionUtils.getStackTrace(malEx)}"
      return false
    }

    log.info "validDeckRedirect($to)..."
    log.info "      toURL=[host:$toURL.host, port:$toURL.port]"
    log.info "deckBaseUrl=[host:$deckBaseUrl.host, port:$deckBaseUrl.port]"
    return toURL.host == deckBaseUrl.host &&
        toURL.port == deckBaseUrl.port
  }
}

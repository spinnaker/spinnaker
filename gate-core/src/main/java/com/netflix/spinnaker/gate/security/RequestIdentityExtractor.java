package com.netflix.spinnaker.gate.security;

import javax.servlet.http.HttpServletRequest;

/**
 * An interface for inspecting an HttpRequest during filter processing where full authentication may
 * not yet have happened.
 */
public interface RequestIdentityExtractor {

  /**
   * @param httpServletRequest the request to inspect
   * @return whether this RequestIdentityExtractor can supply an identity for the given request
   */
  boolean supports(HttpServletRequest httpServletRequest);

  /**
   * @param httpServletRequest the request to inspect
   * @return the identity from the request, or null if no identity is available
   */
  String extractIdentity(HttpServletRequest httpServletRequest);
}

package com.netflix.spinnaker.clouddriver.security

import org.springframework.validation.Errors

interface AllowedAccountsValidator {
  /**
   * Verify that <code>user</code> is allowed to access the account associated with <code>description</code>.
   *
   * If not authorized, an appropriate rejection should be added to <code>errors</code>.
   */
  void validate(String user, Collection<String> allowedAccounts, Object description, Errors errors)
}

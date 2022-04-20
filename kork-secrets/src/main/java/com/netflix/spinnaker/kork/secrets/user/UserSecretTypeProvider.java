package com.netflix.spinnaker.kork.secrets.user;

import java.util.stream.Stream;

/**
 * Provides subtypes of {@link UserSecret} for registration as user secret types. All beans of this
 * type contribute zero or more user secret classes.
 */
public interface UserSecretTypeProvider {
  Stream<? extends Class<? extends UserSecret>> getUserSecretTypes();
}

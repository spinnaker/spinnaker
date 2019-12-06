package com.netflix.spinnaker.fiat.permissions;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

public class DefaultFallbackPermissionsResolver implements FallbackPermissionsResolver {

  private final Authorization fallbackFrom;
  private final Authorization fallbackTo;

  public DefaultFallbackPermissionsResolver(Authorization fallbackFrom, Authorization fallbackTo) {
    this.fallbackFrom = fallbackFrom;
    this.fallbackTo = fallbackTo;
  }

  @Override
  public boolean shouldResolve(@Nonnull Permissions permissions) {
    return permissions.isRestricted() && unpackPermissions(permissions).get(fallbackFrom).isEmpty();
  }

  @Override
  public Permissions resolve(@Nonnull Permissions permissions) {
    Map<Authorization, List<String>> authorizations = unpackPermissions(permissions);
    authorizations.put(fallbackFrom, authorizations.get(fallbackTo));
    return Permissions.Builder.factory(authorizations).build();
  }

  private Map<Authorization, List<String>> unpackPermissions(Permissions permissions) {
    return Arrays.stream(Authorization.values()).collect(toMap(identity(), permissions::get));
  }
}

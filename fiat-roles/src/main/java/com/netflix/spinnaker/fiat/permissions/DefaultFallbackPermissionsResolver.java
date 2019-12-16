package com.netflix.spinnaker.fiat.permissions;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
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
    return permissions.isRestricted() && permissions.unpack().get(fallbackFrom).isEmpty();
  }

  @Override
  public Permissions resolve(@Nonnull Permissions permissions) {
    Map<Authorization, List<String>> authorizations = permissions.unpack();
    authorizations.put(fallbackFrom, authorizations.get(fallbackTo));
    return Permissions.Builder.factory(authorizations).build();
  }
}

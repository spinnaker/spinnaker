package com.netflix.spinnaker.front50.events;

import com.netflix.spinnaker.front50.model.application.Application;

/** Allows mutating an {@link Application} at different {@link Type} hooks. */
public interface ApplicationEventListener {
  /** @return Whether the listener supports {@code type}. */
  boolean supports(Type type);

  /** TODO(rz): Should include Type as part of the signature. */
  Application call(Application originalApplication, Application updatedApplication);

  /** TODO(rz): Should include Type as part of the signature. */
  void rollback(Application originalApplication);

  enum Type {
    PRE_UPDATE,
    POST_UPDATE,
    PRE_CREATE,
    POST_CREATE,
    PRE_DELETE,
    POST_DELETE;
  }
}

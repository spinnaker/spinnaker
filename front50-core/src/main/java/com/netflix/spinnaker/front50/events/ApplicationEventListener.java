package com.netflix.spinnaker.front50.events;

import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/** Allows mutating an {@link Application} at different {@link Type} hooks. */
public interface ApplicationEventListener
    extends Consumer<ApplicationEventListener.ApplicationModelEvent> {
  /** @return Whether the listener supports {@code type}. */
  boolean supports(Type type);

  enum Type {
    PRE_UPDATE,
    POST_UPDATE,
    PRE_CREATE,
    POST_CREATE,
    PRE_DELETE,
    POST_DELETE;
  }

  @NonnullByDefault
  class ApplicationModelEvent {
    /** The {@link Type} of application event. */
    public final Type type;

    /** The original {@link Application} state before modifications. */
    @Nullable public final Application original;

    /** The updated {@link Application} state during modification. */
    public final Application updated;

    public ApplicationModelEvent(Type type, @Nullable Application original, Application updated) {
      this.type = type;
      this.original = original;
      this.updated = updated;
    }

    /** Returns the {@code updated} {@link Application}, which is often the one you want. */
    public Application getApplication() {
      return updated;
    }
  }
}

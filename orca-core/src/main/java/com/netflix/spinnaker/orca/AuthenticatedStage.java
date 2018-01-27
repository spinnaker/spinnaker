package com.netflix.spinnaker.orca;

import java.util.Optional;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.security.User;

/**
 * This interface allows an implementing StageDefinitionBuilder to override the
 * default pipeline authentication context.
 */
public interface AuthenticatedStage {
  Optional<User> authenticatedUser(Stage stage);
}

package com.netflix.spinnaker.clouddriver.aws.userdata;

import javax.annotation.Nonnull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * If enabled, overrides the default Spinnaker user data (represented via implementations of {@link
 * UserDataProvider}.
 */
@Data
@NoArgsConstructor
public class UserDataOverride {
  private boolean enabled;

  /** Identifies the implementation of {@link UserDataTokenizer} to use. */
  @Nonnull private String tokenizerName = "default";
}

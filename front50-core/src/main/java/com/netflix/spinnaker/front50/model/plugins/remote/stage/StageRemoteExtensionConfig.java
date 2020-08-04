package com.netflix.spinnaker.front50.model.plugins.remote.stage;

import com.netflix.spinnaker.front50.model.plugins.remote.RemoteExtension.RemoteExtensionConfig;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StageRemoteExtensionConfig implements RemoteExtensionConfig {

  /** Represents stage type. */
  @Nonnull private String type;

  /** Label to use on the Spinnaker UI while configuring pipeline stages. */
  @Nonnull private String label;

  /** Description to use on the Spinnaker UI while configuring pipeline stages. */
  @Nonnull private String description;

  /** Map of stage parameter names and default values. */
  @Nonnull private Map<String, Object> parameters = new HashMap<>();
}

package com.netflix.spinnaker.front50.model.plugins.remote;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.netflix.spinnaker.front50.model.plugins.PluginInfo;
import com.netflix.spinnaker.front50.model.plugins.remote.stage.StageRemoteExtensionConfig;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

/**
 * A Spinnaker plugin's remote extension configuration.
 *
 * <p>This model is used by Spinnaker to determine which extension points and services require
 * remote extension point configuration.
 *
 * <p>The plugin release {@link PluginInfo.Release#requires} field is used to inform Spinnaker which
 * service to use in configuring the extension point {@link #type} and additionally if the remote
 * extension is compatible with the running version of the Spinnaker service.
 */
@Data
@NoArgsConstructor
public class RemoteExtension {

  /**
   * The remote extension type. The remote extension is configured in the service that implements
   * this extension type.
   */
  @Nonnull private String type;

  /** Identifier of the remote extension. Used for tracing. */
  @Nonnull private String id;

  /**
   * Outbound transport configuration for the remote extension point; the protocol to address it
   * with and the necessary configuration.
   */
  @Nonnull private RemoteExtensionTransport transport = new RemoteExtensionTransport();

  /** Configures the remote extension point. */
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
      property = "type")
  @JsonSubTypes({@JsonSubTypes.Type(value = StageRemoteExtensionConfig.class, name = "stage")})
  @Nullable
  public RemoteExtensionConfig config;

  /** Root remote extension configuration type. */
  public interface RemoteExtensionConfig {}

  @Data
  @NoArgsConstructor
  public static class RemoteExtensionTransport {

    @Nonnull private Http http = new Http();

    @Data
    @NoArgsConstructor
    public static class Http {

      /** URL for remote extension invocation. */
      @URL @Nonnull private String url;

      /** A placeholder for misc. configuration for the underlying HTTP client. */
      @Nonnull private Map<String, Object> config = new HashMap<>();
    }
  }
}

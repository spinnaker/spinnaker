package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.integrations;

import com.netflix.spinnaker.halyard.config.model.v1.node.Features;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
public class IntegrationsConfigWrapper {
  private static final String GREMLIN_API_URL = "https://api.gremlin.com/v1";

  private Integrations integrations;

  public IntegrationsConfigWrapper(final Features features) {
    final boolean isGremlinEnabled = features.getGremlin() != null && features.getGremlin();
    this.integrations = new Integrations(isGremlinEnabled);
  }

  @EqualsAndHashCode(callSuper = false)
  @Data
  private static class Integrations {
    private Gremlin gremlin;

    Integrations(final boolean gremlinEnabled) {
      this.gremlin = new Gremlin(gremlinEnabled);
    }
  }

  @EqualsAndHashCode(callSuper = false)
  @Data
  private static class Gremlin {
    private boolean enabled;
    private String baseUrl;

    Gremlin(final boolean enabled) {
      this.enabled = enabled;
      this.baseUrl = GREMLIN_API_URL;
    }
  }
}

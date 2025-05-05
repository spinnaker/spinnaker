package com.netflix.spinnaker.clouddriver.artifacts.docker;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactProvider;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "docker-registry")
public class HelmOciDockerArtifactProviderProperties
    implements ArtifactProvider<HelmOciDockerArtifactAccount> {
  private boolean enabled;
  private List<HelmOciDockerArtifactAccount> accounts = new ArrayList<>();
}

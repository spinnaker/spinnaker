package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.deck;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.plugins.Plugin;
import com.netflix.spinnaker.halyard.config.services.v1.PluginService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.Profile;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.StringBackedProfileFactory;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PluginManifestProfileFactory extends StringBackedProfileFactory {
  @Autowired PluginService pluginService;

  private static String PLUGIN_ENTRY = "{id: \"%s\", version: \"%s\", url: \"%s\"}";

  @Override
  protected void setProfile(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    profile.appendContents("plugins = [");

    Map<String, Plugin> plugins = pluginService.getPlugins(deploymentConfiguration.getName());
    plugins.entrySet().stream()
        .filter(
            v -> {
              Plugin p = v.getValue();
              return p.getEnabled()
                  && p.getUiResourceLocation() != null
                  && p.getUiResourceLocation() != "";
            })
        .forEach(
            v -> {
              Plugin p = v.getValue();
              profile.appendContents(
                  String.format(
                      PLUGIN_ENTRY, p.getId(), p.getVersion(), p.getUiResourceLocation()));
            });
    profile.appendContents("]");
  }

  @Override
  protected String getRawBaseProfile() {
    return "";
  }

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.DECK;
  }

  @Override
  protected String commentPrefix() {
    return "// ";
  }
}

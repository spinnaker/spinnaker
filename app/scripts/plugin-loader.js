import { Registry } from '@spinnaker/core';

// Appends plugin resources to the bottom of the page via a script
// tag. This makes it so the plugins start loading after the Spinnaker
// application is loaded.
function loadPluginScript(plugin) {
  return new Promise((resolve, reject) => {
    var scriptTag = document.createElement('script');
    scriptTag.src = plugin.location;
    scriptTag.onload = () => resolve();
    scriptTag.onreadystatechange = () => resolve();
    scriptTag.onerror = () => reject();
    document.body.appendChild(scriptTag);
  });
}

// This method grabs all plugins that are defined in Spinnaker settings
// and will call their initialize method and append the script location
// to the bottom of the page. The initialize function is based on the
// interface defined in the plugins module. The Registry is passed into
// the initialize method so the plugin can register itself as a stage.
// This is done by calling window.spinnakerSettings.onPluginLoaded and
// the plugin developer passes in their plugin object that contains
// the initialize method.
export function initPlugins() {
  const plugins = window.spinnakerSettings.plugins;
  window.spinnakerSettings.onPluginLoaded = plugin => plugin.initialize(Registry);
  return Promise.all(plugins.map(p => loadPluginScript(p)));
}

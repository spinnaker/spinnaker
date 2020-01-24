import { module } from 'angular';
import { UIRouter } from '@uirouter/core';
import { IPluginManifest, PluginRegistry } from 'core/plugins/plugin.registry';

export const PLUGINS_MODULE = 'netflix.spinnaker.plugins';
module(PLUGINS_MODULE, ['ui.router']).config([
  '$uiRouterProvider',
  async ($uiRouterProvider: UIRouter) => {
    const pluginRegistry = new PluginRegistry();

    // Tell the router to slow its roll
    $uiRouterProvider.urlService.deferIntercept();

    // Grab all plugins that are defined in plugin-manifest
    // The format for plugin-manifest would be:
    //    const plugins = [{'name':'myPlugin', 'version':'1.2.3', 'devUrl':'/plugins/index.js'}]
    //    export { plugins }
    try {
      const pluginManifestLocation = '/plugin-manifest.js';
      const pluginModule = await import(/* webpackIgnore: true */ pluginManifestLocation);

      if (!pluginModule || !pluginModule.plugins) {
        throw new Error(`Expected plugin-manifest.js to contain an export named 'plugins' but it did not.`);
      } else {
        pluginModule.plugins.forEach((plugin: IPluginManifest) => pluginRegistry.register(plugin));
      }
    } catch (error) {
      console.error('Error registering plugin manifests');
      console.error(error);
    }

    try {
      // Load and initialize them
      await pluginRegistry.loadPlugins();
    } finally {
      // When done, tell the router to initialize
      $uiRouterProvider.urlService.listen();
      $uiRouterProvider.urlService.sync();
    }
  },
]);

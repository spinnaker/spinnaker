import { module } from 'angular';
import { UIRouter } from '@uirouter/core';
import { SETTINGS } from '@spinnaker/core';
import { PluginRegistry } from 'core/plugins/plugin.registry';

export const PLUGINS_MODULE = 'netflix.spinnaker.plugins';
module(PLUGINS_MODULE, ['ui.router']).config([
  '$uiRouterProvider',
  async ($uiRouterProvider: UIRouter) => {
    const pluginRegistry = new PluginRegistry();

    // Tell the router to slow its roll
    $uiRouterProvider.urlService.deferIntercept();

    // Grab all plugins that are defined in Spinnaker settings
    SETTINGS.plugins.forEach(plugin => pluginRegistry.register(plugin));
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

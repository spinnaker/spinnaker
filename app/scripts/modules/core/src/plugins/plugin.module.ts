import { module } from 'angular';
import { UIRouter } from '@uirouter/core';
import { PluginRegistry } from './plugin.registry';
import { sharedLibraries } from './sharedLibraries';

export const PLUGINS_MODULE = 'netflix.spinnaker.plugins';
module(PLUGINS_MODULE, ['ui.router'])
  .config([
    '$uiRouterProvider',
    ($uiRouterProvider: UIRouter) => {
      // Tell the router to slow its roll
      $uiRouterProvider.urlService.deferIntercept();
      sharedLibraries.exposeSharedLibraries();
    },
  ])
  .run([
    '$uiRouter',
    async ($uiRouter: UIRouter) => {
      // TODO: find a better home for this registry
      const pluginRegistry = new PluginRegistry();
      try {
        // Load and initialize plugins
        await Promise.all([pluginRegistry.loadPluginManifestFromDeck(), pluginRegistry.loadPluginManifestFromGate()]);
        await pluginRegistry.loadPlugins();
      } finally {
        // When done, tell the router to initialize
        $uiRouter.urlService.listen();
        $uiRouter.urlService.sync();
      }
    },
  ]);

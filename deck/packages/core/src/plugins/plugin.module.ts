import type { UIRouter } from '@uirouter/core';
import type { IExceptionHandlerService, IRootScopeService } from 'angular';
import { module } from 'angular';

import { PluginRegistry } from './plugin.registry';
import { sharedLibraries } from './sharedLibraries';

export const PLUGINS_MODULE = 'netflix.spinnaker.plugins';

let defaultPluginRegistry = new PluginRegistry();
let defaultInitializationPromise: Promise<void> | undefined;

async function initializePluginRegistry(pluginRegistry: PluginRegistry): Promise<void> {
  sharedLibraries.exposeSharedLibraries();
  await Promise.all([pluginRegistry.loadPluginManifestFromDeck(), pluginRegistry.loadPluginManifestFromGate()]);
  await pluginRegistry.loadPlugins();
}

export function initializePlugins(pluginRegistry?: PluginRegistry): Promise<void> {
  if (pluginRegistry) {
    return initializePluginRegistry(pluginRegistry);
  }
  if (!defaultInitializationPromise) {
    const initializationAttempt = initializePluginRegistry(defaultPluginRegistry);
    const cachedAttempt = initializationAttempt.catch((error) => {
      if (defaultInitializationPromise === cachedAttempt) {
        defaultPluginRegistry = new PluginRegistry();
        defaultInitializationPromise = undefined;
      }
      throw error;
    });
    defaultInitializationPromise = cachedAttempt;
  }
  return defaultInitializationPromise;
}

export function resetPluginInitializationForTests(): void {
  defaultPluginRegistry = new PluginRegistry();
  defaultInitializationPromise = undefined;
}

export function runPlugins(
  $rootScope: IRootScopeService,
  $uiRouter: UIRouter,
  $exceptionHandler: IExceptionHandlerService,
  initializer: () => Promise<void> = initializePlugins,
): void {
  void initializer()
    .catch((error) => $exceptionHandler(error))
    .finally(() => {
      $rootScope.$applyAsync(() => {
        $uiRouter.urlService.listen();
        $uiRouter.urlService.sync();
      });
    });
}

module(PLUGINS_MODULE, ['ui.router'])
  .config([
    '$uiRouterProvider',
    ($uiRouterProvider: UIRouter) => {
      // Tell the router to slow its roll
      $uiRouterProvider.urlService.deferIntercept();
    },
  ])
  .run(['$rootScope', '$uiRouter', '$exceptionHandler', runPlugins]);

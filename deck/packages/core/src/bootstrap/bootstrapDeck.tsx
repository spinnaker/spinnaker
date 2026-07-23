import type { UIRouterReact } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react';
import * as React from 'react';
import { render, unmountComponentAtNode } from 'react-dom';

import { SpinnakerContainer } from './SpinnakerContainer';
import { AngularServices } from '../angular/services';
import { initializeAuthentication } from '../authentication/authentication.module';
import { VersionChecker } from '../config/VersionChecker';
import '../navigation/coreRoutes';
import { getDirectRouter, setDirectRouter } from '../navigation/directRouter';
import { configureRouter, startRouter } from '../navigation/router';
import { initializePlugins } from '../plugins/plugin.module';
import { initializeDynamicRuntimeMetadata, initializeRuntimeMetadata } from './runtimeInitializers';
import { initialize as initializeState } from '../state';

import '../presentation/details.less';
import '../presentation/flex-layout.less';
import '../presentation/main.less';
import '../presentation/navPopover.less';
import '../search/global/globalSearch.less';

let bootstrapRoot: HTMLElement | null = null;
let bootstrapAttempt: Promise<void> | null = null;
let activeRouter: UIRouterReact | null = null;
let renderedRoot: HTMLElement | null = null;
let cacheInitializationAttempt: Promise<void> | null = null;

export function createDeckRoot(router: UIRouterReact): React.ReactElement {
  return (
    <UIRouterContext.Provider value={router}>
      <SpinnakerContainer authenticating={false} routing={false} />
    </UIRouterContext.Provider>
  );
}

function cleanupRuntime(): void {
  const router = activeRouter;
  const root = renderedRoot;
  activeRouter = null;
  renderedRoot = null;

  if (router && getDirectRouter() === router) {
    setDirectRouter(null);
  }
  try {
    if (root) {
      unmountComponentAtNode(root);
    }
  } catch (error) {
    console.error('Failed to unmount Deck runtime', error);
  } finally {
    try {
      router?.dispose();
    } catch (error) {
      console.error('Failed to dispose Deck runtime', error);
    }
  }
}

function initializeInfrastructureCaches(): void {
  if (cacheInitializationAttempt) {
    return;
  }

  cacheInitializationAttempt = Promise.resolve()
    .then(() => AngularServices.cacheInitializer.initialize())
    .then(() => undefined)
    .catch((error) => console.error('Failed to initialize infrastructure caches', error));
}

async function runBootstrap(root: HTMLElement): Promise<void> {
  initializeRuntimeMetadata();
  const authenticated = await initializeAuthentication();
  if (!authenticated) {
    return;
  }

  void initializeDynamicRuntimeMetadata();
  await initializePlugins();
  const router = configureRouter();
  activeRouter = router;
  initializeState();
  initializeInfrastructureCaches();
  await Promise.resolve();

  renderedRoot = root;
  render(createDeckRoot(router), root);
  document.querySelector('.loading-placeholder')?.remove();
  startRouter(router);
}

export function bootstrapDeck(root: HTMLElement | null): Promise<void> {
  if (!root) {
    return Promise.reject(new Error('Cannot bootstrap Deck: #spinnaker-root was not found'));
  }
  if (bootstrapAttempt) {
    return bootstrapRoot === root
      ? bootstrapAttempt
      : Promise.reject(new Error('Cannot bootstrap Deck: a different root already owns the runtime'));
  }

  bootstrapRoot = root;
  bootstrapAttempt = runBootstrap(root).catch((error) => {
    try {
      cleanupRuntime();
    } catch (cleanupError) {
      console.error('Failed to clean up Deck runtime', cleanupError);
    } finally {
      bootstrapRoot = null;
      bootstrapAttempt = null;
    }
    throw error;
  });
  return bootstrapAttempt;
}

export function resetBootstrapDeckForTests(): void {
  try {
    cleanupRuntime();
  } catch (error) {
    console.error('Failed to clean up Deck runtime', error);
  } finally {
    bootstrapRoot = null;
    bootstrapAttempt = null;
    cacheInitializationAttempt = null;
    VersionChecker.resetForTests();
  }
}

/* eslint-disable @spinnaker/import-sort */
// Must be first: sets the jQuery globals before Bootstrap/@spinnaker/core are evaluated.
import './test/vitest/globals';

import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { UIRouterReact } from '@uirouter/react';
import { afterEach, beforeEach } from 'vitest';

import './packages/app/src/settings';
// Initialize the full @spinnaker/core module graph in its canonical order before any spec's
// deep import re-enters it. Under Karma's single webpack bundle this happened implicitly;
// Vitest evaluates a fresh ESM graph per test file, so a deep entry point can otherwise hit
// core's internal circular imports (e.g. Registry -> PipelineRegistry) mid-initialization.
import '@spinnaker/core';
import { installMockHttpSupport } from './packages/core/src/api/mock/mockHttpSupport';
import { getDirectRouter, setDirectRouter } from './packages/core/src/navigation/directRouter';
import { initialize as initializeReactStates } from './packages/core/src/state';
import { installCssVarResolver } from './test/vitest/cssVars';
import { installJsdomLayout } from './test/vitest/jsdomLayout';
import { registerVitestCompat } from './test/vitest/matchers';

Error.stackTraceLimit = Infinity;

registerVitestCompat();

// jsdom does not resolve CSS custom properties (var(--x)) in getComputedStyle; a few specs assert
// resolved colors from loaded LESS. Restore browser-equivalent resolution without weakening tests.
installCssVarResolver();

// jsdom performs no layout (box metrics are 0, percentage dimensions are not resolved). Provide a
// minimal layout model so the few layout-dependent specs match browser behavior. Installed after
// the CSS-variable resolver so the two getComputedStyle wrappers compose.
installJsdomLayout();

// Initialize the shared filter model/service singletons (ClusterState/FunctionState/...).
// The app does this in bootstrapDeck; under Karma's single shared context some spec ran it
// globally. Vitest isolates each file, so initialize per file to match that expectation.
initializeReactStates();

let directTestRouter: UIRouterReact;
beforeEach(() => {
  directTestRouter = new UIRouterReact();
  setDirectRouter(directTestRouter);
});
afterEach(() => {
  cleanup();
  if (getDirectRouter() === directTestRouter) {
    setDirectRouter(null);
  }
  directTestRouter.dispose();
});

installMockHttpSupport();

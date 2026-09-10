import { shallow } from 'enzyme';
import React from 'react';
import { UIRouterReact } from '@uirouter/react';

import { createDeckRuntime } from '../../bootstrap/DeckRuntime';
import { SETTINGS } from '../../config/settings';
import { configureRouter } from '../../navigation/router';
import { SpinErrorBoundary } from '../../presentation';
import { SearchV1 } from './SearchV1';
import { SearchV2 } from './SearchV2';

import './infrastructure.states';

describe('infrastructure states', () => {
  const originalSearchVersion = SETTINGS.searchVersion;

  function createRouter(): UIRouterReact {
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    router.disposable(runtime);
    return configureRouter(router, runtime.services, runtime.routingState);
  }

  afterEach(() => {
    SETTINGS.searchVersion = originalSearchVersion;
  });

  it('registers direct V1 search and its one-shot route parameter', () => {
    SETTINGS.searchVersion = 1;
    const router = createRouter();
    const searchState = router.stateRegistry.get('home.search');
    const view = searchState.views['main@'];
    const errorBoundary = shallow(React.createElement(view.component));

    expect(errorBoundary.type()).toBe(SpinErrorBoundary);
    // React 17's dev-mode forwardRef defines `displayName` as a non-enumerable
    // getter/setter (for its own dev warnings), so it doesn't survive whatever
    // enumerable-keys-only copy @uirouter/react's state/view registration does
    // internally on its way through the state registry. The `render` function
    // reference is unaffected and reliably identifies the routed component.
    expect((errorBoundary.prop('children').type as any).render).toBe((SearchV1 as any).render);
    expect(view.$type).toBe('react');
    expect(searchState.url).toContain('&route');
    expect(searchState.params.route.dynamic).toBe(true);
    router.dispose();
  });

  it('registers direct V2 search when configured', () => {
    SETTINGS.searchVersion = 2;
    const router = createRouter();
    const searchState = router.stateRegistry.get('home.search');
    const view = searchState.views['main@'];
    const errorBoundary = shallow(React.createElement(view.component));

    expect(errorBoundary.type()).toBe(SpinErrorBoundary);
    // See the V1 test above for why `render` is compared instead of `displayName`.
    expect((errorBoundary.prop('children').type as any).render).toBe((SearchV2 as any).render);
    expect(view.$type).toBe('react');
    router.dispose();
  });
});

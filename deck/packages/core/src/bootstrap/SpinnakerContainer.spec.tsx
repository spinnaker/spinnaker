import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { act, render } from '@testing-library/react';
import React from 'react';

import { createDeckRuntime } from './DeckRuntime';
import { DeckRuntimeContext } from './DeckRuntimeContext';
import { SpinnakerContainer } from './SpinnakerContainer';
import { GlobalBannerService } from '../banner/global/GlobalBannerService';
import { configureRouter } from '../navigation/router';

describe('SpinnakerContainer', () => {
  it('renders the transition overlay from RoutingState and unsubscribes on unmount', () => {
    vi.spyOn(GlobalBannerService, 'getActiveBanners').mockReturnValue(Promise.resolve([]));
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    const routingState = runtime.routingState;
    configureRouter(router, runtime.services, routingState);
    const actualSubscribe = routingState.subscribe.bind(routingState);
    const unsubscribe = vi.fn();
    vi.spyOn(routingState, 'subscribe').mockImplementation((listener) => {
      const dispose = actualSubscribe(listener);
      return () => {
        unsubscribe();
        dispose();
      };
    });
    const { container, unmount } = render(
      <DeckRuntimeContext.Provider value={runtime}>
        <UIRouterContext.Provider value={router}>
          <SpinnakerContainer authenticating={false} routingState={routingState} />
        </UIRouterContext.Provider>
      </DeckRuntimeContext.Provider>,
    );

    expect(container.querySelector('.transition-overlay')).not.toBeInTheDocument();

    let finish: () => void;
    act(() => {
      finish = routingState.begin();
    });
    expect(container.querySelector('.transition-overlay')).toBeInTheDocument();

    act(() => finish());
    expect(container.querySelector('.transition-overlay')).not.toBeInTheDocument();

    unmount();
    expect(unsubscribe).toHaveBeenCalledTimes(1);

    router.dispose();
    runtime.dispose();
  });
});

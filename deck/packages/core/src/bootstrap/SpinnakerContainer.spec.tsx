import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import { mount } from 'enzyme';
import React from 'react';

import { createDeckRuntime } from './DeckRuntime';
import { DeckRuntimeContext } from './DeckRuntimeContext';
import { SpinnakerContainer } from './SpinnakerContainer';
import { GlobalBannerService } from '../banner/global/GlobalBannerService';
import { configureRouter } from '../navigation/router';

describe('SpinnakerContainer', () => {
  it('renders the transition overlay from RoutingState and unsubscribes on unmount', () => {
    spyOn(GlobalBannerService, 'getActiveBanners').and.returnValue(Promise.resolve([]));
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    const routingState = runtime.routingState;
    configureRouter(router, runtime.services, routingState);
    const actualSubscribe = routingState.subscribe.bind(routingState);
    const unsubscribe = jasmine.createSpy('unsubscribe');
    spyOn(routingState, 'subscribe').and.callFake((listener) => {
      const dispose = actualSubscribe(listener);
      return () => {
        unsubscribe();
        dispose();
      };
    });
    const wrapper = mount(
      <DeckRuntimeContext.Provider value={runtime}>
        <UIRouterContext.Provider value={router}>
          <SpinnakerContainer authenticating={false} routingState={routingState} />
        </UIRouterContext.Provider>
      </DeckRuntimeContext.Provider>,
    );

    expect(wrapper.find('.transition-overlay').exists()).toBe(false);

    const finish = routingState.begin();
    wrapper.update();
    expect(wrapper.find('.transition-overlay').exists()).toBe(true);

    finish();
    wrapper.update();
    expect(wrapper.find('.transition-overlay').exists()).toBe(false);

    wrapper.unmount();
    expect(unsubscribe).toHaveBeenCalledTimes(1);

    router.dispose();
    runtime.dispose();
  });
});

import { UIRouter, UIRouterReact } from '@uirouter/react';
import { mount } from 'enzyme';
import React from 'react';
import { RecoilRoot } from 'recoil';

import { AuthenticationService } from '../authentication/AuthenticationService';
import { GlobalBannerService } from '../banner/global/GlobalBannerService';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { DeckRuntimeContext } from '../bootstrap/DeckRuntimeContext';
import { configureRouter } from '../navigation/router';
import { SpinnakerHeaderContent } from './SpinnakerHeader';

describe('SpinnakerHeader', () => {
  beforeEach(() => {
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ roles: [] } as any);
    spyOn(GlobalBannerService, 'getActiveBanners').and.returnValue(Promise.resolve([]));
  });

  it('renders primary navigation with the legacy navbar class contract', () => {
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    router.disposable(runtime);
    configureRouter(router, runtime.services);
    const wrapper = mount(
      <UIRouter router={router}>
        <DeckRuntimeContext.Provider value={runtime}>
          <RecoilRoot>
            <SpinnakerHeaderContent />
          </RecoilRoot>
        </DeckRuntimeContext.Provider>
      </UIRouter>,
    );

    const primaryNav = wrapper.find('ul.page-nav');

    expect(primaryNav.hasClass('navbar-nav')).toBe(true);
    wrapper.unmount();
    router.dispose();
  });
});

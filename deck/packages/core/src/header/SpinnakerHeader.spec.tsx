import { UIRouter, UIRouterReact } from '@uirouter/react';
import { render } from '@testing-library/react';
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
    vi.spyOn(AuthenticationService, 'getAuthenticatedUser').mockReturnValue({ roles: [] } as any);
    vi.spyOn(GlobalBannerService, 'getActiveBanners').mockReturnValue(Promise.resolve([]));
  });

  it('renders primary navigation with the legacy navbar class contract', () => {
    const router = new UIRouterReact();
    const runtime = createDeckRuntime(router);
    router.disposable(runtime);
    configureRouter(router, runtime.services, runtime.routingState);
    const { container, unmount } = render(
      <UIRouter router={router}>
        <DeckRuntimeContext.Provider value={runtime}>
          <RecoilRoot>
            <SpinnakerHeaderContent />
          </RecoilRoot>
        </DeckRuntimeContext.Provider>
      </UIRouter>,
    );

    expect(container.querySelector('ul.page-nav')).toHaveClass('navbar-nav');
    unmount();
    router.dispose();
  });
});

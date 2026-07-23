import { UIRouter } from '@uirouter/react';
import { mount } from 'enzyme';
import React from 'react';
import { RecoilRoot } from 'recoil';

import { AuthenticationService } from '../authentication/AuthenticationService';
import { GlobalBannerService } from '../banner/global/GlobalBannerService';
import { configureRouter } from '../navigation/router';
import { SpinnakerHeaderContent } from './SpinnakerHeader';

describe('SpinnakerHeader', () => {
  beforeEach(() => {
    spyOn(AuthenticationService, 'getAuthenticatedUser').and.returnValue({ roles: [] } as any);
    spyOn(GlobalBannerService, 'getActiveBanners').and.returnValue(Promise.resolve([]));
  });

  it('renders primary navigation with the legacy navbar class contract', () => {
    const wrapper = mount(
      <UIRouter router={configureRouter()}>
        <RecoilRoot>
          <SpinnakerHeaderContent />
        </RecoilRoot>
      </UIRouter>,
    );

    const primaryNav = wrapper.find('ul.page-nav');

    expect(primaryNav.hasClass('navbar-nav')).toBe(true);
  });
});

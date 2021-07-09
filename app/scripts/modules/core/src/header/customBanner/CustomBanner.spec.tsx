import React from 'react';
import { shallow } from 'enzyme';

import { Application } from '../../application/application.model';
import { ICustomBannerConfig } from '../../application/config/customBanner/CustomBannerConfig';
import { getTestBannerConfigs } from '../../application/config/customBanner/CustomBannerConfig.spec';

import { CustomBanner } from './CustomBanner';

describe('<CustomBanner />', () => {
  let application: Application;
  let wrapper: any;
  let bannerConfigs: ICustomBannerConfig[];

  beforeEach(() => {
    application = new Application('my-app', null, []);
    wrapper = shallow(<CustomBanner />);
    bannerConfigs = getTestBannerConfigs();
  });

  describe('view', () => {
    it('renders no banner by default', () => {
      expect(wrapper.state('bannerConfig')).toBeNull();
      expect(wrapper.find('.custom-banner').length).toEqual(0);
    });

    it('renders banner when appropriate', () => {
      wrapper.setState({ bannerConfig: bannerConfigs[0] });
      expect(wrapper.find('.custom-banner').length).toEqual(1);
    });

    describe('functionality', () => {
      it('updates state appropriately when no enabled banner found on app attributes', () => {
        expect(wrapper.state('bannerConfig')).toBeNull();
        application.attributes.customBanners = null;
        wrapper.instance().updateBannerConfig(application.attributes);
        expect(wrapper.state('bannerConfig')).toBeNull();
      });
      it('updates state appropriately when enabled banner found on app attributes', () => {
        expect(wrapper.state('bannerConfig')).toBeNull();
        application.attributes.customBanners = bannerConfigs;
        wrapper.instance().updateBannerConfig(application.attributes);
        expect(wrapper.state('bannerConfig')).toEqual(bannerConfigs[0]);
      });
    });
  });
});

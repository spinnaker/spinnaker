import React from 'react';
import { shallow } from 'enzyme';

import { noop } from '../../../utils';

import { CustomBannerConfig, ICustomBannerConfig } from './CustomBannerConfig';

describe('<CustomBannerConfig />', () => {
  let bannerConfigs: ICustomBannerConfig[];
  let wrapper: any;

  beforeEach(() => {
    bannerConfigs = getTestBannerConfigs();
    wrapper = shallow(
      <CustomBannerConfig
        bannerConfigs={bannerConfigs}
        isSaving={false}
        saveError={false}
        updateBannerConfigs={noop}
      />,
    );
  });

  describe('view', () => {
    it('renders a row for each banner config', () => {
      expect(wrapper.find('.custom-banner-config-row').length).toEqual(bannerConfigs.length);
    });
    it('renders an "add" button', () => {
      expect(wrapper.find('.add-new').length).toEqual(1);
    });
  });

  describe('functionality', () => {
    it('update banner config', () => {
      expect(wrapper.state('bannerConfigsEditing')).toEqual(bannerConfigs);
      wrapper
        .find('input[type="checkbox"]')
        .at(1)
        .simulate('change', { target: { checked: true } });
      const updatedConfigs = [
        {
          ...bannerConfigs[0],
          enabled: false,
        },
        {
          ...bannerConfigs[1],
          enabled: true,
        },
      ];
      expect(wrapper.state('bannerConfigsEditing')).toEqual(updatedConfigs);
    });
    it('add banner config', () => {
      expect(wrapper.state('bannerConfigsEditing').length).toEqual(2);
      wrapper.find('.add-new').simulate('click');
      expect(wrapper.state('bannerConfigsEditing').length).toEqual(3);
    });
    it('remove banner config', () => {
      expect(wrapper.state('bannerConfigsEditing').length).toEqual(2);
      wrapper.find('.custom-banner-config-remove').at(1).simulate('click');
      expect(wrapper.state('bannerConfigsEditing').length).toEqual(1);
    });
  });
});

export function getTestBannerConfigs(): ICustomBannerConfig[] {
  return [
    {
      backgroundColor: 'var(--color-alert)',
      enabled: true,
      text: 'Warning: currently in maintenance mode',
      textColor: 'var(--color-text-on-dark)',
    },
    {
      backgroundColor: 'var(--color-alert)',
      enabled: false,
      text: 'Warning: currently in production freeze',
      textColor: 'var(--color-text-on-dark)',
    },
  ];
}

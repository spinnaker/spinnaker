import { shallow } from 'enzyme';
import React from 'react';

import { StageConfigField } from '@spinnaker/core';

import type { IManifestDeploymentOptionsProps } from './ManifestDeploymentOptions';
import { defaultTrafficManagementConfig, ManifestDeploymentOptions } from './ManifestDeploymentOptions';

describe('<ManifestDeploymentOptions />', () => {
  const onConfigChangeSpy = jasmine.createSpy('onConfigChangeSpy');
  let wrapper: any;
  let props: IManifestDeploymentOptionsProps;

  beforeEach(() => {
    props = {
      accounts: [],
      config: defaultTrafficManagementConfig,
      onConfigChange: onConfigChangeSpy,
      selectedAccount: null,
    };
    wrapper = shallow(<ManifestDeploymentOptions {...props} />);
  });

  describe('view', () => {
    it('renders only the enable checkbox when config is disabled', () => {
      expect(wrapper.find(StageConfigField).length).toEqual(1);
      expect(wrapper.find('input[type="checkbox"]').length).toEqual(1);
    });
    it('renders config fields for `namespace`, `services`, `enableTraffic`, and `strategy` when config is enabled', () => {
      props.config.enabled = true;
      wrapper = shallow(<ManifestDeploymentOptions {...props} />);
      expect(wrapper.find(StageConfigField).length).toEqual(5);
    });
  });

  describe('functionality', () => {
    it('updates `config.enabled` when enable checkbox is toggled', () => {
      wrapper
        .find('input[type="checkbox"]')
        .at(0)
        .simulate('change', { target: { checked: true } });
      expect(onConfigChangeSpy).toHaveBeenCalledWith({
        ...defaultTrafficManagementConfig,
        enabled: true,
      });
    });
    it('disables the traffic checkbox when a non-None rollout strategy is selected', () => {
      props.config.options.strategy = 'redblack';
      wrapper = shallow(<ManifestDeploymentOptions {...props} />);
      expect(wrapper.find('input[type="checkbox"]').at(1).props().disabled).toEqual(true);
    });
    it('disables the traffic checkbox when blue/green rollout strategy is selected', () => {
      props.config.options.strategy = 'bluegreen';
      wrapper = shallow(<ManifestDeploymentOptions {...props} />);
      expect(wrapper.find('input[type="checkbox"]').at(1).props().disabled).toEqual(true);
    });

    it('strategy bluegreen should not display warning label', () => {
      props.config.options.strategy = 'bluegreen';
      wrapper = shallow(<ManifestDeploymentOptions {...props} />);
      expect(wrapper.find('p[id="redBlackWarning"]').exists()).toEqual(false);
    });
    it('strategy highlander should not display warning label', () => {
      props.config.options.strategy = 'highlander';
      wrapper = shallow(<ManifestDeploymentOptions {...props} />);
      expect(wrapper.find('p[id="redBlackWarning"]').exists()).toEqual(false);
    });

    it('strategy redblack should display warning label', () => {
      props.config.options.strategy = 'redblack';
      wrapper = shallow(<ManifestDeploymentOptions {...props} />);
      expect(wrapper.find('p[id="redBlackWarning"]').exists()).toEqual(true);
    });
  });
});

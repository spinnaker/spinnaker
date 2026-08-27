import React from 'react';
import { mount } from 'enzyme';

import { FleetInstanceBadge } from './FleetInstanceBadge';
import { SETTINGS } from '../config/settings';

describe('FleetInstanceBadge', () => {
  afterEach(() => SETTINGS.resetToOriginal());

  it('renders nothing when fleet settings are absent', () => {
    SETTINGS.fleet = undefined;

    const wrapper = mount(<FleetInstanceBadge />);

    expect(wrapper.html()).toBeNull();
  });

  it('renders nothing when instanceId is not set', () => {
    SETTINGS.fleet = { enabled: true, globalUrl: 'https://spinnaker.example.com' };

    const wrapper = mount(<FleetInstanceBadge />);

    expect(wrapper.html()).toBeNull();
  });

  it('shows the instanceId when set', () => {
    SETTINGS.fleet = { enabled: true, globalUrl: 'https://spinnaker.example.com', instanceId: 'inst-1' };

    const wrapper = mount(<FleetInstanceBadge />);

    expect(wrapper.text()).toContain('inst-1');
  });
});

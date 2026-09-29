import { mount } from 'enzyme';
import React from 'react';

import { Tooltip } from '@spinnaker/core';

import { ManifestStatus } from './ManifestStatus';

describe('<ManifestStatus />', () => {
  const status = (overrides: { [key: string]: { state: boolean; message?: string } }) => ({
    available: { state: true },
    stable: { state: true },
    paused: { state: false },
    failed: { state: false },
    ...overrides,
  });

  it('renders status messages as plain text rather than Markdown', () => {
    const message = 'Degraded: [see details](https://example.com) ![x](https://example.com/x.png) <b>bold</b>';
    const wrapper = mount(<ManifestStatus status={status({ stable: { state: false, message } }) as any} />);

    expect(wrapper.find('.band-active').text()).toBe('Transitioning');

    const tooltip = wrapper.find(Tooltip);
    expect(tooltip.length).toBe(1);
    expect(tooltip.prop('value')).toBeFalsy();

    const content = mount(tooltip.prop('template'));
    expect(content.text()).toBe(message);
    expect(content.find('a').length).toBe(0);
    expect(content.find('img').length).toBe(0);
    expect(content.find('b').length).toBe(0);
  });

  it('shows no tooltip content when there is no message', () => {
    const wrapper = mount(<ManifestStatus status={status({ available: { state: false } }) as any} />);

    expect(wrapper.find('.band-warning').text()).toBe('Not Fully Available');
    expect(wrapper.find(Tooltip).prop('template')).toBeUndefined();
  });

  it('shows the paused band with its message', () => {
    const wrapper = mount(
      <ManifestStatus status={status({ paused: { state: true, message: 'Paused by user' } }) as any} />,
    );

    expect(wrapper.find('.band-info').text()).toBe('Rollout Paused');
    expect(mount(wrapper.find(Tooltip).prop('template')).text()).toBe('Paused by user');
  });
});

import { mount } from 'enzyme';
import React from 'react';

import { ScopeClusterSelector } from './ScopeClusterSelector';

describe('<ScopeClusterSelector />', () => {
  it('uses link-styled buttons to toggle between dropdown and free-form modes', () => {
    const component = mount(
      <ScopeClusterSelector clusters={['api']} model="" onChange={jasmine.createSpy('onChange')} />,
    );

    let toggle = component.find('button[type="button"].btn-link.clickable');

    expect(toggle.length).toBe(1);
    if (!toggle.length) {
      return;
    }

    expect(toggle.text()).toBe('Toggle for text input');
    toggle.simulate('click');

    expect(component.find('select').length).toBe(0);
    expect(component.find('input[type="text"]').length).toBe(1);

    toggle = component.find('button[type="button"].btn-link.clickable');
    expect(toggle.length).toBe(1);
    expect(toggle.text()).toBe('Toggle for list of existing clusters');
    toggle.simulate('click');

    expect(component.find('select').length).toBe(1);
    expect(component.find('input[type="text"]').length).toBe(0);
  });
});

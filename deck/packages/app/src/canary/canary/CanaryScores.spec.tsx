import { mount } from 'enzyme';
import React from 'react';

import { CanaryScores } from './CanaryScores';

import '@spinnaker/core/presentation/main.less';

const dangerBorderColor = 'rgb(255, 0, 0)';

describe('<CanaryScores />', () => {
  let previousDangerColor: string;

  beforeAll(() => {
    previousDangerColor = document.documentElement.style.getPropertyValue('--color-danger');
    document.documentElement.style.setProperty('--color-danger', dangerBorderColor);
  });

  afterAll(() => {
    document.documentElement.style.setProperty('--color-danger', previousDangerColor);
  });

  it('styles directly invalid score inputs', () => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const wrapper = mount(
      <CanaryScores successfulScore="50" unhealthyScore="60" onChange={jasmine.createSpy('onChange')} />,
      { attachTo: host },
    );
    const inputs = wrapper.find('input');

    expect(inputs.at(0).hasClass('form-control')).toBe(true);
    expect(inputs.at(0).hasClass('invalid')).toBe(true);
    expect(inputs.at(0).hasClass('dirty')).toBe(false);
    expect(inputs.at(1).hasClass('invalid')).toBe(true);
    expect(window.getComputedStyle(inputs.at(0).getDOMNode()).borderColor).toBe(dangerBorderColor);

    wrapper.unmount();
    host.remove();
  });
});

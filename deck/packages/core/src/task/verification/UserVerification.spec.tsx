import { mount } from 'enzyme';
import React from 'react';

import { UserVerification } from './UserVerification';

import '../../presentation/main.less';

const dangerBorderColor = 'rgb(255, 0, 0)';

describe('UserVerification', () => {
  let previousDangerColor: string;

  beforeAll(() => {
    previousDangerColor = document.documentElement.style.getPropertyValue('--color-danger');
    document.documentElement.style.setProperty('--color-danger', dangerBorderColor);
  });

  afterAll(() => {
    document.documentElement.style.setProperty('--color-danger', previousDangerColor);
  });

  it('loads its verification styles', () => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const wrapper = mount(<UserVerification expectedValue="production" onValidChange={() => undefined} />, {
      attachTo: host,
    });

    const verification = wrapper.find('.user-verification').getDOMNode() as HTMLElement;
    const verificationText = wrapper.find('.verification-text').getDOMNode() as HTMLElement;
    const input = wrapper.find('input');

    expect(window.getComputedStyle(verification).textAlign).toBe('right');
    expect(window.getComputedStyle(verificationText).fontWeight).toBe('600');
    expect(input.hasClass('invalid')).toBe(true);
    expect(input.hasClass('highlight-pristine')).toBe(true);
    expect(window.getComputedStyle(input.getDOMNode()).borderColor).toBe(dangerBorderColor);

    input.simulate('change', { target: { value: 'production' } });

    expect(wrapper.find('input').hasClass('invalid')).toBe(false);

    wrapper.unmount();
    host.remove();
  });
});

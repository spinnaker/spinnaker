import { mount } from 'enzyme';
import React from 'react';

import { UserVerification } from './UserVerification';

describe('UserVerification', () => {
  it('loads its verification styles', () => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const wrapper = mount(<UserVerification expectedValue="production" onValidChange={() => undefined} />, {
      attachTo: host,
    });

    const verification = wrapper.find('.user-verification').getDOMNode() as HTMLElement;
    const verificationText = wrapper.find('.verification-text').getDOMNode() as HTMLElement;

    expect(window.getComputedStyle(verification).textAlign).toBe('right');
    expect(window.getComputedStyle(verificationText).fontWeight).toBe('600');

    wrapper.unmount();
    host.remove();
  });
});

import { shallow } from 'enzyme';
import React from 'react';

import { Spinner } from './Spinner';

describe('Spinner', () => {
  it('uses the nano horizontal spinner when the circular SVG component is unavailable', () => {
    const wrapper = shallow(<Spinner className="test-spinner" color="#123456" mode="circular" size="large" />);

    expect(wrapper.is('div.load.nano.test-spinner')).toBe(true);
    expect(wrapper.find('.bar').length).toBe(1);
  });
});

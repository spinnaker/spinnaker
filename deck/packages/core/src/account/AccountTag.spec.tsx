import { shallow } from 'enzyme';
import React from 'react';

import { AccountTag } from './AccountTag';

describe('AccountTag', () => {
  it('renders nothing for missing account values', () => {
    const wrapper = shallow(<AccountTag account={null} />);

    expect(wrapper.isEmptyRender()).toBe(true);
  });
});

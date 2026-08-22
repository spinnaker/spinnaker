import { shallow } from 'enzyme';
import React from 'react';

import { StyleguideRoute } from './StyleguideRoute';

describe('<StyleguideRoute />', () => {
  it('StyleguideRoute renders the styleguide iframe', () => {
    const wrapper = shallow(<StyleguideRoute />);
    const iframe = wrapper.find('iframe');

    expect(iframe.prop('src')).toBe('/styleguide.html');
    expect(iframe.prop('title')).toBe('Spinnaker styleguide');
    expect(iframe.prop('style')).toEqual({ border: 0, height: 'calc(100vh - 60px)', width: '100%' });
  });
});

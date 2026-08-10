import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import { RenderWhenVisible } from './RenderWhenVisible';

describe('<RenderWhenVisible />', () => {
  beforeEach(() => {
    (window as any).IntersectionObserver = class {
      public observe() {}
      public disconnect() {}
    };
  });

  it('renders when hidden content changes from zero height to non-zero height', async () => {
    const wrapper = mount(<RenderWhenVisible placeholderHeight={0} render={() => <span>content</span>} />);

    await act(async () => {
      wrapper.setProps({ placeholderHeight: 110 });
    });
    wrapper.update();

    expect(wrapper.find('span').text()).toEqual('content');
  });

  it('renders zero-height content immediately', () => {
    const wrapper = mount(<RenderWhenVisible placeholderHeight={0} render={() => <span>content</span>} />);

    expect(wrapper.find('span').text()).toEqual('content');
  });
});

import React from 'react';
import { mount } from 'enzyme';
import { useDeepObjectDiff } from './useDeepObjectDiff.hook';

const { useEffect } = React;

describe('useDeepObjectDiff', () => {
  it('changes its return value when the object has changed between renders', () => {
    const spy = jasmine.createSpy('useEffect callback');
    function TestComponent(props: any) {
      useEffect(spy, [useDeepObjectDiff(props)]);
      return null as JSX.Element;
    }

    const wrapper = mount(<TestComponent prop={123} />);
    wrapper.setProps({ prop: 123 });
    wrapper.setProps({ prop: 123 });
    wrapper.setProps({ prop: 123 });
    wrapper.setProps({ prop: 123 });
    expect(spy).toHaveBeenCalledTimes(1);

    wrapper.setProps({ prop: 456 });
    expect(spy).toHaveBeenCalledTimes(2);
  });
});

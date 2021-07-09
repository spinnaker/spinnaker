import React from 'react';

import { useIsMountedRef } from '..';
import { mount } from 'enzyme';

describe('useIsMountedRef hook', () => {
  let useIsMountedRefSpy: jasmine.Spy;
  let TestComponent: React.FunctionComponent;
  let ref: React.RefObject<boolean>;
  let isMountedInRender: boolean;

  beforeEach(() => {
    useIsMountedRefSpy = jasmine.createSpy('useIsMountedRef', useIsMountedRef).and.callThrough();

    TestComponent = function () {
      ref = useIsMountedRefSpy();
      isMountedInRender = ref.current;
      return null;
    };
  });

  it('ref.current is false inside the initial render', () => {
    mount(<TestComponent />);
    expect(useIsMountedRefSpy).toHaveBeenCalledTimes(1);
    expect(isMountedInRender).toBe(false);
  });

  it('ref.current is true after the initial render', () => {
    mount(<TestComponent />);
    expect(useIsMountedRefSpy).toHaveBeenCalledTimes(1);
    expect(ref.current).toBe(true);
  });

  it('ref.current is true during and after the next render', () => {
    const component = mount(<TestComponent />);
    component.setProps({});
    expect(useIsMountedRefSpy).toHaveBeenCalledTimes(2);
    expect(isMountedInRender).toBe(true);
    expect(ref.current).toBe(true);
  });

  it('ref.current remains true on subsequent renders', () => {
    const component = mount(<TestComponent />);
    component.setProps({});
    component.setProps({});
    component.setProps({});
    expect(useIsMountedRefSpy).toHaveBeenCalledTimes(4);
    expect(isMountedInRender).toBe(true);
    expect(ref.current).toBe(true);
  });

  it('ref.current is false after the component unmounts', (done) => {
    const component = mount(<TestComponent />);
    expect(ref.current).toBe(true);

    component.unmount();
    setTimeout(() => {
      expect(ref.current).toBe(false);
      done();
    });
  });
});

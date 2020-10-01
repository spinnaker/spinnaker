import React from 'react';
import { mount } from 'enzyme';
import { useInterval } from './useInterval.hook';

describe('useInterval hook', () => {
  beforeEach(() => jasmine.clock().install());
  afterEach(() => jasmine.clock().uninstall());

  function Component(props: any) {
    const { callback, interval } = props;

    useInterval(callback, interval);

    return <></>;
  }

  it('calls the callback on the specified interval', () => {
    const spy = jasmine.createSpy('callback');
    const component = mount(<Component callback={spy} interval={1000} />);

    expect(spy).toHaveBeenCalledTimes(0);

    // Tick forward to slightly before the polling interval should kick in
    jasmine.clock().tick(900);
    component.setProps({});

    expect(spy).toHaveBeenCalledTimes(0);

    // Tick forward to past the first interval
    jasmine.clock().tick(200);
    component.setProps({});
    expect(spy).toHaveBeenCalledTimes(1);

    // Second interval
    jasmine.clock().tick(1000);
    component.setProps({});
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('resets the interval when changed', () => {
    const spy = jasmine.createSpy('callback');
    const component = mount(<Component callback={spy} interval={1000} />);

    expect(spy).toHaveBeenCalledTimes(0);

    // Tick forward to slightly before the polling interval should kick in
    jasmine.clock().tick(900);
    component.setProps({});

    expect(spy).toHaveBeenCalledTimes(0);

    // Change / reset interval
    component.setProps({ interval: 5000 });

    // Tick forward to slightly past the original interval (clock is now at 1200)
    jasmine.clock().tick(200);
    component.setProps({});
    expect(spy).toHaveBeenCalledTimes(0);

    // Tick forward to first iteration of new interval
    jasmine.clock().tick(5000);
    component.setProps({});
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('does not call a callback when none is provided', () => {
    const spy = jasmine.createSpy('callback');
    const component = mount(<Component callback={spy} interval={1000} />);

    expect(spy).toHaveBeenCalledTimes(0);

    jasmine.clock().tick(1200);
    component.setProps({});

    expect(spy).toHaveBeenCalledTimes(1);

    component.setProps({ callback: null });

    // Because we got rid of the callback, the hook should stop the interval and
    // throw away the previous callback
    jasmine.clock().tick(1200);
    component.setProps({});
    expect(spy).toHaveBeenCalledTimes(1);

    const newSpy = jasmine.createSpy('newCallback');

    component.setProps({ callback: newSpy });

    // Passing a new callback after null should restart the interval
    expect(newSpy).toHaveBeenCalledTimes(0);

    jasmine.clock().tick(1200);
    component.setProps({});
    expect(newSpy).toHaveBeenCalledTimes(1);
  });
});

import React from 'react';
import { mount } from 'enzyme';
import { useLatestCallback } from './useLatestCallback.hook';

const TestComponent = ({
  callback,
  onCallbackChange,
}: {
  callback: (...args: any) => any;
  onCallbackChange: (callback: Function) => any;
}) => {
  const memoizedCallback = useLatestCallback(callback);
  onCallbackChange(memoizedCallback);
  return null as JSX.Element;
};

describe('useLatestCallback', () => {
  it('should give back a stable function reference when the callback argument changes', () => {
    const callbacksFromHook: any[] = [];
    const onCallbackChange = (callback: any) => callbacksFromHook.push(callback);

    const component = mount(<TestComponent callback={() => 'first'} onCallbackChange={onCallbackChange} />);

    component.setProps({ callback: () => 'second' });

    expect(callbacksFromHook.length).toBe(2);
    expect(callbacksFromHook[0]).toBe(callbacksFromHook[1]);

    component.setProps({ callback: () => 'third' });

    expect(callbacksFromHook.length).toBe(3);
    expect(callbacksFromHook[1]).toBe(callbacksFromHook[2]);
  });

  it('should always call the latest callback argument', () => {
    let callbackFromHook: any;
    const onCallbackChange = (callback: any) => (callbackFromHook = callback);

    const initialCallback = jasmine.createSpy('initialCallback', () => 'initial');

    const component = mount(<TestComponent callback={initialCallback} onCallbackChange={onCallbackChange} />);

    callbackFromHook();
    expect(initialCallback).toHaveBeenCalledTimes(1);

    const updatedCallback = jasmine.createSpy('updatedCallback', () => 'updated');

    component.setProps({ callback: updatedCallback });

    callbackFromHook();
    expect(initialCallback).toHaveBeenCalledTimes(1);
    expect(updatedCallback).toHaveBeenCalledTimes(1);
  });

  it('should pass through the arguments/return value of the original callback', () => {
    let callbackFromHook: any;
    const onCallbackChange = (callback: any) => (callbackFromHook = callback);

    mount(<TestComponent callback={(value) => `Hello ${value}`} onCallbackChange={onCallbackChange} />);

    const returnValue = callbackFromHook('World');
    expect(returnValue).toBe('Hello World');
  });
});

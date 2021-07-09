import React from 'react';

import { useDebouncedValue } from './useDebouncedValue.hook';
import { mount } from 'enzyme';

describe('useDebouncedValue hook', () => {
  beforeEach(() => jasmine.clock().install());
  afterEach(() => jasmine.clock().uninstall());

  const timeoutMillis = 1000;
  function Component(props: any) {
    const { value, onChange, millis } = props;
    const [debounced, isDebouncing] = useDebouncedValue(value, millis);

    React.useEffect(() => onChange(value, debounced, isDebouncing), [value, debounced, isDebouncing]);
    return <></>;
  }

  it('initially, debounced value is the same as the initial value', () => {
    const spy = jasmine.createSpy();
    mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith('a', 'a', jasmine.anything());
  });

  it('initially, isDebouncing is false', () => {
    const spy = jasmine.createSpy();
    mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith(jasmine.anything(), jasmine.anything(), false);
  });

  it('isDebounced is true during the time where the value is different than the debounced value', () => {
    const spy = jasmine.createSpy();
    const component = mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    component.setProps({ value: 'b' });
    expect(spy).toHaveBeenCalledTimes(2);
    const [value, debouncedValue, isDebouncing] = spy.calls.mostRecent().args;
    expect([value, debouncedValue, isDebouncing]).toEqual(['b', 'a', true]);
  });

  it('after the timeout, debounced should equal value and isDebouncing is false', () => {
    const spy = jasmine.createSpy();
    const component = mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    component.setProps({ value: 'b' });

    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy.calls.mostRecent().args).toEqual(['b', 'a', true]);

    jasmine.clock().tick(timeoutMillis);
    component.setProps({}); // rerender

    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.calls.mostRecent().args).toEqual(['b', 'b', false]);
  });

  it('does not update debounced value until after the timeout', () => {
    const spy = jasmine.createSpy();
    const component = mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    component.setProps({ value: 'b' });

    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy.calls.mostRecent().args).toEqual(['b', 'a', true]);

    jasmine.clock().tick(timeoutMillis - 1);
    component.setProps({}); // rerender
    expect(spy).toHaveBeenCalledTimes(2);

    jasmine.clock().tick(1);
    component.setProps({}); // rerender
    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.calls.mostRecent().args).toEqual(['b', 'b', false]);
  });

  it('coalesces multiple values into a single debounced value', () => {
    const spy = jasmine.createSpy();
    const component = mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    component.setProps({ value: 'b' });
    component.setProps({ value: 'c' });
    component.setProps({ value: 'd' });
    component.setProps({ value: 'e' });

    expect(spy).toHaveBeenCalledTimes(5);
    expect(spy.calls.allArgs()).toEqual([
      ['a', 'a', false],
      ['b', 'a', true],
      ['c', 'a', true],
      ['d', 'a', true],
      ['e', 'a', true],
    ]);

    jasmine.clock().tick(timeoutMillis);
    component.setProps({}); // rerender

    expect(spy).toHaveBeenCalledTimes(6);
    expect(spy.calls.mostRecent().args).toEqual(['e', 'e', false]);
  });

  it('resets the timeout when a new value is seen but the previous value hasnt been debounced yet', () => {
    const spy = jasmine.createSpy();
    const component = mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    component.setProps({ value: 'b' });

    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy.calls.mostRecent().args).toEqual(['b', 'a', true]);

    const halfTimeoutMillis = timeoutMillis / 2;
    // Wait 500ms -- change the value to 'c' before 'b' is debounced
    jasmine.clock().tick(halfTimeoutMillis); // clock is now 500ms
    component.setProps({ value: 'c' });
    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.calls.mostRecent().args).toEqual(['c', 'a', true]);

    // Wait 500ms more.  Debounced should still be 'a'
    jasmine.clock().tick(halfTimeoutMillis); // clock is now 1000ms
    component.setProps({ value: 'c' });
    expect(spy).toHaveBeenCalledTimes(3);

    // Wait 500ms more.  Debounced should now be 'c'
    jasmine.clock().tick(halfTimeoutMillis); // clock is now 1500ms
    component.setProps({ value: 'c' });
    expect(spy).toHaveBeenCalledTimes(4);
    expect(spy.calls.mostRecent().args).toEqual(['c', 'c', false]);
  });
});

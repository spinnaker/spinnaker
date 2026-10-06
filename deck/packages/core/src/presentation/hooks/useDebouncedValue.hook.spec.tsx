import { mount } from 'enzyme';
import React from 'react';

import { useDebouncedValue } from './useDebouncedValue.hook';

describe('useDebouncedValue hook', () => {
  beforeEach(() =>
    vi.useFakeTimers({
      toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
    }),
  );
  afterEach(() => vi.useRealTimers());

  const timeoutMillis = 1000;
  function Component(props: any) {
    const { value, onChange, millis } = props;
    const [debounced, isDebouncing] = useDebouncedValue(value, millis);

    React.useEffect(() => onChange(value, debounced, isDebouncing), [value, debounced, isDebouncing]);
    return <></>;
  }

  it('initially, debounced value is the same as the initial value', () => {
    const spy = vi.fn();
    mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith('a', 'a', expect.anything());
  });

  it('initially, isDebouncing is false', () => {
    const spy = vi.fn();
    mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith(expect.anything(), expect.anything(), false);
  });

  it('isDebounced is true during the time where the value is different than the debounced value', () => {
    const spy = vi.fn();
    const component = mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    component.setProps({ value: 'b' });
    expect(spy).toHaveBeenCalledTimes(2);
    const [value, debouncedValue, isDebouncing] = spy.mock.lastCall;
    expect([value, debouncedValue, isDebouncing]).toEqual(['b', 'a', true]);
  });

  it('after the timeout, debounced should equal value and isDebouncing is false', () => {
    const spy = vi.fn();
    const component = mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    component.setProps({ value: 'b' });

    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy.mock.lastCall).toEqual(['b', 'a', true]);

    vi.advanceTimersByTime(timeoutMillis);
    component.setProps({}); // rerender

    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.mock.lastCall).toEqual(['b', 'b', false]);
  });

  it('does not update debounced value until after the timeout', () => {
    const spy = vi.fn();
    const component = mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    component.setProps({ value: 'b' });

    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy.mock.lastCall).toEqual(['b', 'a', true]);

    vi.advanceTimersByTime(timeoutMillis - 1);
    component.setProps({}); // rerender
    expect(spy).toHaveBeenCalledTimes(2);

    vi.advanceTimersByTime(1);
    component.setProps({}); // rerender
    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.mock.lastCall).toEqual(['b', 'b', false]);
  });

  it('coalesces multiple values into a single debounced value', () => {
    const spy = vi.fn();
    const component = mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    component.setProps({ value: 'b' });
    component.setProps({ value: 'c' });
    component.setProps({ value: 'd' });
    component.setProps({ value: 'e' });

    expect(spy).toHaveBeenCalledTimes(5);
    expect(spy.mock.calls).toEqual([
      ['a', 'a', false],
      ['b', 'a', true],
      ['c', 'a', true],
      ['d', 'a', true],
      ['e', 'a', true],
    ]);

    vi.advanceTimersByTime(timeoutMillis);
    component.setProps({}); // rerender

    expect(spy).toHaveBeenCalledTimes(6);
    expect(spy.mock.lastCall).toEqual(['e', 'e', false]);
  });

  it('resets the timeout when a new value is seen but the previous value hasnt been debounced yet', () => {
    const spy = vi.fn();
    const component = mount(<Component value="a" onChange={spy} millis={timeoutMillis} />);
    component.setProps({ value: 'b' });

    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy.mock.lastCall).toEqual(['b', 'a', true]);

    const halfTimeoutMillis = timeoutMillis / 2;
    // Wait 500ms -- change the value to 'c' before 'b' is debounced
    vi.advanceTimersByTime(halfTimeoutMillis); // clock is now 500ms
    component.setProps({ value: 'c' });
    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.mock.lastCall).toEqual(['c', 'a', true]);

    // Wait 500ms more.  Debounced should still be 'a'
    vi.advanceTimersByTime(halfTimeoutMillis); // clock is now 1000ms
    component.setProps({ value: 'c' });
    expect(spy).toHaveBeenCalledTimes(3);

    // Wait 500ms more.  Debounced should now be 'c'
    vi.advanceTimersByTime(halfTimeoutMillis); // clock is now 1500ms
    component.setProps({ value: 'c' });
    expect(spy).toHaveBeenCalledTimes(4);
    expect(spy.mock.lastCall).toEqual(['c', 'c', false]);
  });
});

import { act } from '@testing-library/react';
import React from 'react';

import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import { useDebouncedValue } from './useDebouncedValue.hook';

describe('useDebouncedValue hook', () => {
  beforeEach(() =>
    vi.useFakeTimers({
      toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
    }),
  );
  afterEach(() => vi.useRealTimers());

  const timeoutMillis = 1000;
  interface IHookProps {
    value: string;
    onChange: (...args: any[]) => void;
    millis: number;
  }

  function renderDebouncedValue(props: IHookProps) {
    return renderHookHarness(({ value, onChange, millis }: IHookProps) => {
      const result = useDebouncedValue(value, millis);
      const [debounced, isDebouncing] = result;

      React.useEffect(() => onChange(value, debounced, isDebouncing), [value, debounced, isDebouncing]);
      return result;
    }, props);
  }

  it('initially, debounced value is the same as the initial value', () => {
    const spy = vi.fn();
    const rendered = renderDebouncedValue({ value: 'a', onChange: spy, millis: timeoutMillis });
    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith('a', 'a', expect.anything());
    expect(rendered.result.current).toEqual(['a', false]);
  });

  it('initially, isDebouncing is false', () => {
    const spy = vi.fn();
    const rendered = renderDebouncedValue({ value: 'a', onChange: spy, millis: timeoutMillis });
    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith(expect.anything(), expect.anything(), false);
    expect(rendered.result.current).toEqual(['a', false]);
  });

  it('isDebounced is true during the time where the value is different than the debounced value', () => {
    const spy = vi.fn();
    const rendered = renderDebouncedValue({ value: 'a', onChange: spy, millis: timeoutMillis });
    rendered.rerenderHook({ value: 'b', onChange: spy, millis: timeoutMillis });
    expect(spy).toHaveBeenCalledTimes(2);
    const [value, debouncedValue, isDebouncing] = spy.mock.lastCall;
    expect([value, debouncedValue, isDebouncing]).toEqual(['b', 'a', true]);
  });

  it('after the timeout, debounced should equal value and isDebouncing is false', () => {
    const spy = vi.fn();
    const rendered = renderDebouncedValue({ value: 'a', onChange: spy, millis: timeoutMillis });
    rendered.rerenderHook({ value: 'b', onChange: spy, millis: timeoutMillis });

    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy.mock.lastCall).toEqual(['b', 'a', true]);

    act(() => vi.advanceTimersByTime(timeoutMillis));

    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.mock.lastCall).toEqual(['b', 'b', false]);
  });

  it('does not update debounced value until after the timeout', () => {
    const spy = vi.fn();
    const rendered = renderDebouncedValue({ value: 'a', onChange: spy, millis: timeoutMillis });
    rendered.rerenderHook({ value: 'b', onChange: spy, millis: timeoutMillis });

    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy.mock.lastCall).toEqual(['b', 'a', true]);

    act(() => vi.advanceTimersByTime(timeoutMillis - 1));
    expect(spy).toHaveBeenCalledTimes(2);

    act(() => vi.advanceTimersByTime(1));
    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.mock.lastCall).toEqual(['b', 'b', false]);
  });

  it('coalesces multiple values into a single debounced value', () => {
    const spy = vi.fn();
    const rendered = renderDebouncedValue({ value: 'a', onChange: spy, millis: timeoutMillis });
    rendered.rerenderHook({ value: 'b', onChange: spy, millis: timeoutMillis });
    rendered.rerenderHook({ value: 'c', onChange: spy, millis: timeoutMillis });
    rendered.rerenderHook({ value: 'd', onChange: spy, millis: timeoutMillis });
    rendered.rerenderHook({ value: 'e', onChange: spy, millis: timeoutMillis });

    expect(spy).toHaveBeenCalledTimes(5);
    expect(spy.mock.calls).toEqual([
      ['a', 'a', false],
      ['b', 'a', true],
      ['c', 'a', true],
      ['d', 'a', true],
      ['e', 'a', true],
    ]);

    act(() => vi.advanceTimersByTime(timeoutMillis));

    expect(spy).toHaveBeenCalledTimes(6);
    expect(spy.mock.lastCall).toEqual(['e', 'e', false]);
  });

  it('resets the timeout when a new value is seen but the previous value hasnt been debounced yet', () => {
    const spy = vi.fn();
    const rendered = renderDebouncedValue({ value: 'a', onChange: spy, millis: timeoutMillis });
    rendered.rerenderHook({ value: 'b', onChange: spy, millis: timeoutMillis });

    expect(spy).toHaveBeenCalledTimes(2);
    expect(spy.mock.lastCall).toEqual(['b', 'a', true]);

    const halfTimeoutMillis = timeoutMillis / 2;
    // Wait 500ms -- change the value to 'c' before 'b' is debounced
    act(() => vi.advanceTimersByTime(halfTimeoutMillis)); // clock is now 500ms
    rendered.rerenderHook({ value: 'c', onChange: spy, millis: timeoutMillis });
    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.mock.lastCall).toEqual(['c', 'a', true]);

    // Wait 500ms more.  Debounced should still be 'a'
    act(() => vi.advanceTimersByTime(halfTimeoutMillis)); // clock is now 1000ms
    expect(spy).toHaveBeenCalledTimes(3);

    // Wait 500ms more.  Debounced should now be 'c'
    act(() => vi.advanceTimersByTime(halfTimeoutMillis)); // clock is now 1500ms
    expect(spy).toHaveBeenCalledTimes(4);
    expect(spy.mock.lastCall).toEqual(['c', 'c', false]);
  });
});

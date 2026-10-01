import { act } from '@testing-library/react';

import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import { useInterval } from './useInterval.hook';

describe('useInterval hook', () => {
  beforeEach(() =>
    vi.useFakeTimers({
      toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
    }),
  );
  afterEach(() => vi.useRealTimers());

  interface IHookProps {
    callback: (() => any) | null;
    interval: number;
  }

  function renderInterval(props: IHookProps) {
    return renderHookHarness(({ callback, interval }) => useInterval(callback as () => any, interval), props);
  }

  it('calls the callback on the specified interval', () => {
    const spy = vi.fn();
    const rendered = renderInterval({ callback: spy, interval: 1000 });

    expect(spy).toHaveBeenCalledTimes(0);

    // Tick forward to slightly before the polling interval should kick in
    act(() => vi.advanceTimersByTime(900));

    expect(spy).toHaveBeenCalledTimes(0);

    // Tick forward to past the first interval
    act(() => vi.advanceTimersByTime(200));
    expect(spy).toHaveBeenCalledTimes(1);

    // Second interval
    act(() => vi.advanceTimersByTime(1000));
    expect(spy).toHaveBeenCalledTimes(2);

    rendered.unmount();
    act(() => vi.advanceTimersByTime(1000));
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('resets the interval when changed', () => {
    const spy = vi.fn();
    const rendered = renderInterval({ callback: spy, interval: 1000 });

    expect(spy).toHaveBeenCalledTimes(0);

    // Tick forward to slightly before the polling interval should kick in
    act(() => vi.advanceTimersByTime(900));

    expect(spy).toHaveBeenCalledTimes(0);

    // Change / reset interval
    rendered.rerenderHook({ callback: spy, interval: 5000 });

    // Tick forward to slightly past the original interval (clock is now at 1200)
    act(() => vi.advanceTimersByTime(200));
    expect(spy).toHaveBeenCalledTimes(0);

    // Tick forward to first iteration of new interval
    act(() => vi.advanceTimersByTime(5000));
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('does not call a callback when none is provided', () => {
    const spy = vi.fn();
    const rendered = renderInterval({ callback: spy, interval: 1000 });

    expect(spy).toHaveBeenCalledTimes(0);

    act(() => vi.advanceTimersByTime(1200));

    expect(spy).toHaveBeenCalledTimes(1);

    rendered.rerenderHook({ callback: null, interval: 1000 });

    // Because we got rid of the callback, the hook should stop the interval and
    // throw away the previous callback
    act(() => vi.advanceTimersByTime(1200));
    expect(spy).toHaveBeenCalledTimes(1);

    const newSpy = vi.fn();

    rendered.rerenderHook({ callback: newSpy, interval: 1000 });

    // Passing a new callback after null should restart the interval
    expect(newSpy).toHaveBeenCalledTimes(0);

    act(() => vi.advanceTimersByTime(1200));
    expect(newSpy).toHaveBeenCalledTimes(1);
  });
});

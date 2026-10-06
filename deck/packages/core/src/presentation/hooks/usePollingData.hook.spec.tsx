import { act, waitFor } from '@testing-library/react';
import { useEffect } from 'react';

import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import type { IUseLatestPromiseResult } from './useLatestPromise.hook';
import { usePollingData } from './usePollingData.hook';

describe('usePollingData hook', () => {
  beforeEach(() =>
    vi.useFakeTimers({
      toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'],
    }),
  );
  afterEach(() => vi.useRealTimers());

  // Remove the the refresh function for .isEqual assertions
  function promiseState(call: IUseLatestPromiseResult<any>) {
    const { refresh, ...state } = call;
    return state;
  }

  interface IHookProps {
    promiseFactory: () => PromiseLike<any>;
    defaultValue: any;
    pollingInterval: number;
    deps: any[];
    onChange: (result: IUseLatestPromiseResult<any>) => void;
  }

  function renderPollingData(props: IHookProps) {
    return renderHookHarness(({ promiseFactory, defaultValue, pollingInterval, deps, onChange }: IHookProps) => {
      const hookResult = usePollingData(promiseFactory, defaultValue, pollingInterval, deps);
      const { status, result, error, requestId } = hookResult;

      useEffect(() => onChange(hookResult), [status, result, error, requestId]);
      return hookResult;
    }, props);
  }

  function defer<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason: any) => void;
    const promise = new Promise<T>((_resolve, _reject) => {
      resolve = _resolve;
      reject = _reject;
    });
    return { promise, resolve, reject };
  }

  it('delegates to useData', () => {
    const spy = vi.fn();
    const deferred = defer<string>();
    const factory = () => deferred.promise;
    const rendered = renderPollingData({
      promiseFactory: factory,
      deps: ['foo'],
      defaultValue: 'default',
      pollingInterval: 1000,
      onChange: spy,
    });

    expect(spy).toHaveBeenCalledTimes(2);

    expect(promiseState(spy.mock.calls[0][0])).toEqual({
      status: 'NONE',
      result: 'default',
      error: undefined,
      requestId: 0,
    });

    expect(promiseState(spy.mock.calls[1][0])).toEqual({
      status: 'PENDING',
      result: 'default',
      error: undefined,
      requestId: 0,
    });
    expect(promiseState(rendered.result.current)).toEqual(promiseState(spy.mock.calls[1][0]));
  });

  it('calls the factory on the specified polling interval', async () => {
    const spy = vi.fn();
    let deferred = defer<string>();
    const factory = vi.fn().mockImplementation(() => deferred.promise);
    const rendered = renderPollingData({
      promiseFactory: factory,
      deps: ['foo'],
      defaultValue: 'default',
      pollingInterval: 1000,
      onChange: spy,
    });

    await act(async () => {
      deferred.resolve('result');
      await deferred.promise;
    });
    await waitFor(() => expect(rendered.result.current.status).toBe('RESOLVED'));
    expect(factory).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledTimes(3);
    expect(promiseState(spy.mock.calls[2][0])).toEqual({
      status: 'RESOLVED',
      result: 'result',
      error: undefined,
      requestId: 0,
    });

    // Reset to a new deferred promise to simulate calling
    // a real factory function again
    deferred = defer<string>();

    // Tick forward to slightly before the polling interval should kick in
    act(() => vi.advanceTimersByTime(900));

    // Confirm that ticking forward didn't trigger a refresh
    expect(factory).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledTimes(3);

    // Clock is now at 1400ms, 400ms past the polling interval
    act(() => vi.advanceTimersByTime(500));

    // Confirm that the useData result was refreshed
    expect(factory).toHaveBeenCalledTimes(2);
    expect(spy).toHaveBeenCalledTimes(4);

    expect(promiseState(spy.mock.calls[3][0])).toEqual({
      status: 'PENDING',
      result: 'result',
      error: undefined,
      requestId: 1,
    });

    rendered.unmount();
    act(() => vi.advanceTimersByTime(1000));
    expect(factory).toHaveBeenCalledTimes(2);
  });
});

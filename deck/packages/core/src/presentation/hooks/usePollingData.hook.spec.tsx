import { mount } from 'enzyme';
import React, { useEffect } from 'react';

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

  function Component(props: any) {
    const { promiseFactory, defaultValue, pollingInterval, deps, onChange } = props;
    const useDataResult: IUseLatestPromiseResult<any> = usePollingData(
      promiseFactory,
      defaultValue,
      pollingInterval,
      deps,
    );
    const { status, result, error, requestId } = useDataResult;

    useEffect(() => onChange(useDataResult), [status, result, error, requestId]);

    return <></>;
  }

  function defer() {
    let resolve: Function, reject: Function;
    const promise = new Promise((_resolve, _reject) => {
      resolve = _resolve;
      reject = _reject;
    });
    return { promise, resolve, reject };
  }

  it('delegates to useData', () => {
    const spy = vi.fn();
    const deferred = defer();
    const factory = () => deferred.promise;
    mount(
      <Component
        promiseFactory={factory}
        deps={['foo']}
        defaultValue={'default'}
        pollingInterval={1000}
        onChange={spy}
      />,
    );

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
  });

  it('calls the factory on the specified polling interval', async () => {
    const spy = vi.fn();
    let deferred = defer();
    const factory = vi.fn().mockImplementation(() => deferred.promise);
    const component = mount(
      <Component
        promiseFactory={factory}
        deps={['foo']}
        defaultValue="default"
        pollingInterval={1000}
        onChange={spy}
      />,
    );

    deferred.resolve('result');
    await deferred.promise;
    component.setProps({});
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
    deferred = defer();

    // Tick forward to slightly before the polling interval should kick in
    vi.advanceTimersByTime(900);
    component.setProps({});

    // Confirm that ticking forward didn't trigger a refresh
    expect(factory).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledTimes(3);

    // Clock is now at 1400ms, 400ms past the polling interval
    vi.advanceTimersByTime(500);
    component.setProps({});

    // Confirm that the useData result was refreshed
    expect(factory).toHaveBeenCalledTimes(2);
    expect(spy).toHaveBeenCalledTimes(4);

    expect(promiseState(spy.mock.calls[3][0])).toEqual({
      status: 'PENDING',
      result: 'result',
      error: undefined,
      requestId: 1,
    });
  });
});

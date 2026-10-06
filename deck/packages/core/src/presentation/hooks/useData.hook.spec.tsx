import { act, waitFor } from '@testing-library/react';
import React from 'react';

import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import { useData } from './useData.hook';
import type { IUseLatestPromiseResult } from './useLatestPromise.hook';

describe('useData hook', () => {
  interface IHookProps {
    promiseFactory: () => PromiseLike<any>;
    deps: any[];
    onChange: (result: IUseLatestPromiseResult<any>) => void;
    defaultValue: any;
  }

  function renderData(props: IHookProps) {
    return renderHookHarness(({ promiseFactory, deps, onChange, defaultValue }: IHookProps) => {
      const hookResult = useData(promiseFactory, defaultValue, deps);
      const { status, result, error, requestId } = hookResult;

      React.useEffect(() => onChange(hookResult), [status, result, error, requestId]);
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

  it('the factory is not called when some deps are null', () => {
    const spy = vi.fn();
    const factory = vi.fn();
    renderData({ promiseFactory: factory, deps: ['foo', null], defaultValue: 'default', onChange: spy });
    expect(factory).toHaveBeenCalledTimes(0);
  });

  it('the factory is not called when some deps are undefined', () => {
    const spy = vi.fn();
    const factory = vi.fn();
    renderData({ promiseFactory: factory, deps: ['foo', undefined], defaultValue: 'default', onChange: spy });
    expect(factory).toHaveBeenCalledTimes(0);
  });

  it('the default result is returned until the promise resolves', async () => {
    const spy = vi.fn();
    const deferred = defer<string>();
    const factory = () => deferred.promise;
    const rendered = renderData({ promiseFactory: factory, deps: ['foo'], defaultValue: 'default', onChange: spy });

    expect(spy).toHaveBeenCalledTimes(2);

    expect(spy.mock.calls[0][0]).toEqual({
      status: 'NONE',
      result: 'default',
      error: undefined,
      requestId: 0,
      refresh: expect.any(Function),
    });

    expect(spy.mock.calls[1][0]).toEqual({
      status: 'PENDING',
      result: 'default',
      error: undefined,
      requestId: 0,
      refresh: expect.any(Function),
    });

    await act(async () => {
      deferred.resolve('result');
      await deferred.promise;
    });
    await waitFor(() => expect(rendered.result.current.status).toBe('RESOLVED'));
    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.mock.calls[2][0]).toEqual({
      status: 'RESOLVED',
      result: 'result',
      error: undefined,
      requestId: 0,
      refresh: expect.any(Function),
    });
  });

  it('the default result is returned until the first result is seen (even if deps are falsey)', async () => {
    const spy = vi.fn();
    const deferred = defer<string>();
    const factory = () => deferred.promise;
    const rendered = renderData({ promiseFactory: factory, deps: [null], defaultValue: 'default', onChange: spy });

    expect(spy).toHaveBeenCalledTimes(1);

    expect(spy.mock.calls[0][0]).toEqual({
      status: 'NONE',
      result: 'default',
      error: undefined,
      requestId: 0,
      refresh: expect.any(Function),
    });

    rendered.rerenderHook({ promiseFactory: factory, deps: ['foo'], defaultValue: 'default', onChange: spy });

    expect(spy).toHaveBeenCalledTimes(2);

    expect(spy.mock.calls[1][0]).toEqual({
      status: 'PENDING',
      result: 'default',
      error: undefined,
      requestId: 1,
      refresh: expect.any(Function),
    });

    await act(async () => {
      deferred.resolve('result');
      await deferred.promise;
    });
    await waitFor(() => expect(rendered.result.current.status).toBe('RESOLVED'));
    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.mock.calls[2][0]).toEqual({
      status: 'RESOLVED',
      result: 'result',
      error: undefined,
      requestId: 1,
      refresh: expect.any(Function),
    });
  });
});

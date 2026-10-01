import { act, waitFor } from '@testing-library/react';
import React from 'react';

import { renderHookHarness } from '../../utils/testUtils/hookHarness';
import type { IUseLatestPromiseResult } from './useLatestPromise.hook';
import { useLatestPromise } from './useLatestPromise.hook';

describe('useLatestPromise hook', () => {
  // Remove the the refresh function for .isEqual assertions
  function promiseState(call: IUseLatestPromiseResult<any>) {
    const { refresh, ...state } = call;
    return state;
  }

  interface IHookProps {
    promiseFactory: () => PromiseLike<any>;
    deps: any[];
    onChange: (result: IUseLatestPromiseResult<any>) => void;
  }

  function renderLatestPromise(props: IHookProps) {
    return renderHookHarness(({ promiseFactory, deps, onChange }: IHookProps) => {
      const hookResult = useLatestPromise(promiseFactory, deps);
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

  it('has status NONE if no promise has been returned', () => {
    const spy = vi.fn();
    const rendered = renderLatestPromise({ promiseFactory: () => null as any, deps: [], onChange: spy });
    expect(spy).toHaveBeenCalledTimes(1);

    expect(promiseState(rendered.result.current)).toEqual({
      status: 'NONE',
      result: undefined,
      error: undefined,
      requestId: 0,
    });
  });

  it('has status PENDING if a promise has been returned but has not yet resolved', () => {
    const spy = vi.fn();
    const deferred = defer<string>();
    const rendered = renderLatestPromise({ promiseFactory: () => deferred.promise, deps: [], onChange: spy });
    expect(spy).toHaveBeenCalledTimes(2);

    expect(promiseState(rendered.result.current)).toEqual({
      status: 'PENDING',
      result: undefined,
      error: undefined,
      requestId: 0,
    });
  });

  it('has status RESOLVED if a promise resolved', async () => {
    const spy = vi.fn();
    const deferred = defer<string>();
    const rendered = renderLatestPromise({ promiseFactory: () => deferred.promise, deps: [], onChange: spy });
    expect(spy).toHaveBeenCalledTimes(2);

    await act(async () => {
      deferred.resolve('payload');
      await deferred.promise;
    });
    await waitFor(() => expect(rendered.result.current.status).toBe('RESOLVED'));

    expect(spy).toHaveBeenCalledTimes(3);
    expect(promiseState(rendered.result.current)).toEqual({
      status: 'RESOLVED',
      result: 'payload',
      error: undefined,
      requestId: 0,
    });
  });

  it('has status REJECTED if a promise rejected', async () => {
    const spy = vi.fn();
    const deferred = defer<string>();
    const rendered = renderLatestPromise({ promiseFactory: () => deferred.promise, deps: [], onChange: spy });
    expect(spy).toHaveBeenCalledTimes(2);

    await act(async () => {
      deferred.reject('error');
      await expect(deferred.promise).rejects.toBe('error');
    });
    await waitFor(() => expect(rendered.result.current.status).toBe('REJECTED'));

    expect(spy).toHaveBeenCalledTimes(3);
    expect(promiseState(rendered.result.current)).toEqual({
      status: 'REJECTED',
      result: undefined,
      error: 'error',
      requestId: 0,
    });
  });

  it('only handles the latest promise when multiple promises are pending', async () => {
    const spy = vi.fn();
    const deferred1 = defer<string>();
    const rendered = renderLatestPromise({ promiseFactory: () => deferred1.promise, deps: [1], onChange: spy });
    expect(spy).toHaveBeenCalledTimes(2);

    const deferred2 = defer<string>();
    rendered.rerenderHook({ promiseFactory: () => deferred2.promise, deps: [2], onChange: spy });
    expect(spy).toHaveBeenCalledTimes(3);
    expect(rendered.result.current.status).toEqual('PENDING');

    await act(async () => {
      deferred1.resolve('payload1');
      await deferred1.promise;
    });

    expect(spy).toHaveBeenCalledTimes(3);
    expect(rendered.result.current.status).toEqual('PENDING');

    await act(async () => {
      deferred2.resolve('payload2');
      await deferred2.promise;
    });
    await waitFor(() => expect(rendered.result.current.status).toBe('RESOLVED'));

    expect(spy).toHaveBeenCalledTimes(4);
    expect(promiseState(rendered.result.current)).toEqual({
      status: 'RESOLVED',
      result: 'payload2',
      error: undefined,
      requestId: 1,
    });
  });

  it('gets a new promise if refresh() is called', async () => {
    const spy = vi.fn();
    const deferred = defer<string>();
    const promiseFactorySpy = vi.fn().mockImplementation(() => deferred.promise);
    const rendered = renderLatestPromise({ promiseFactory: promiseFactorySpy, deps: [], onChange: spy });
    expect(promiseFactorySpy).toHaveBeenCalledTimes(1);

    // initial promise is resolved.
    await act(async () => {
      deferred.resolve('payload');
      await deferred.promise;
    });

    act(() => rendered.result.current.refresh());
    expect(promiseFactorySpy).toHaveBeenCalledTimes(2);
  });

  it('ignores old pending results if a newer promise is being processed', async () => {
    const spy = vi.fn();
    const deferred1 = defer<string>();
    const deferred2 = defer<string>();
    const rendered = renderLatestPromise({ promiseFactory: () => deferred1.promise, deps: [1], onChange: spy });

    rendered.rerenderHook({ promiseFactory: () => deferred2.promise, deps: [2], onChange: spy });

    // The first promise is resolved.
    await act(async () => {
      deferred1.resolve('payload1');
      await deferred1.promise;
    });

    // The second promise is resolved.
    await act(async () => {
      deferred2.resolve('payload2');
      await deferred2.promise;
    });
    await waitFor(() => expect(rendered.result.current.status).toBe('RESOLVED'));

    expect(spy).toHaveBeenCalledTimes(4);
    const allCalls = spy.mock.calls.map((args) => promiseState(args[0]));
    expect(allCalls[0]).toEqual({ status: 'NONE', result: undefined, error: undefined, requestId: 0 });
    // initial request
    expect(allCalls[1]).toEqual({ status: 'PENDING', result: undefined, error: undefined, requestId: 0 });
    // second request
    expect(allCalls[2]).toEqual({ status: 'PENDING', result: undefined, error: undefined, requestId: 1 });
    // resolved second request
    expect(allCalls[3]).toEqual({ status: 'RESOLVED', result: 'payload2', error: undefined, requestId: 1 });
  });
});

import React, { useEffect } from 'react';
import { mount } from 'enzyme';
import { IUseLatestPromiseResult } from './useLatestPromise.hook';
import { usePollingData } from './usePollingData.hook';

describe('usePollingData hook', () => {
  beforeEach(() => jasmine.clock().install());
  afterEach(() => jasmine.clock().uninstall());

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
    const spy = jasmine.createSpy('onChange');
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

    expect(promiseState(spy.calls.argsFor(0)[0])).toEqual({
      status: 'NONE',
      result: 'default',
      error: undefined,
      requestId: 0,
    });

    expect(promiseState(spy.calls.argsFor(1)[0])).toEqual({
      status: 'PENDING',
      result: 'default',
      error: undefined,
      requestId: 0,
    });
  });

  it('calls the factory on the specified polling interval', async () => {
    const spy = jasmine.createSpy('onChange');
    let deferred = defer();
    const factory = jasmine.createSpy('factory').and.callFake(() => deferred.promise);
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
    expect(promiseState(spy.calls.argsFor(2)[0])).toEqual({
      status: 'RESOLVED',
      result: 'result',
      error: undefined,
      requestId: 0,
    });

    // Reset to a new deferred promise to simulate calling
    // a real factory function again
    deferred = defer();

    // Tick forward to slightly before the polling interval should kick in
    jasmine.clock().tick(900);
    component.setProps({});

    // Confirm that ticking forward didn't trigger a refresh
    expect(factory).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledTimes(3);

    // Clock is now at 1400ms, 400ms past the polling interval
    jasmine.clock().tick(500);
    component.setProps({});

    // Confirm that the useData result was refreshed
    expect(factory).toHaveBeenCalledTimes(2);
    expect(spy).toHaveBeenCalledTimes(4);

    expect(promiseState(spy.calls.argsFor(3)[0])).toEqual({
      status: 'PENDING',
      result: 'result',
      error: undefined,
      requestId: 1,
    });
  });
});

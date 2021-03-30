import React from 'react';
import { mount } from 'enzyme';
import { IUseLatestPromiseResult, useLatestPromise } from './useLatestPromise.hook';

describe('useLatestPromise hook', () => {
  // Remove the the refresh function for .isEqual assertions
  function promiseState(call: IUseLatestPromiseResult<any>) {
    const { refresh, ...state } = call;
    return state;
  }

  function Component(props: any) {
    const { promiseFactory, deps, onChange } = props;
    const useLatestPromiseResult: IUseLatestPromiseResult<any> = useLatestPromise(promiseFactory, deps);
    const { status, result, error, requestId } = useLatestPromiseResult;

    React.useEffect(() => onChange(useLatestPromiseResult), [status, result, error, requestId]);
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

  it('has status NONE if no promise has been returned', () => {
    const spy = jasmine.createSpy('onChange');
    mount(<Component promiseFactory={() => null as any} deps={[]} onChange={spy} />);
    expect(spy).toHaveBeenCalledTimes(1);

    const result: IUseLatestPromiseResult<any> = spy.calls.mostRecent().args[0];
    expect(promiseState(result)).toEqual({ status: 'NONE', result: undefined, error: undefined, requestId: 0 });
  });

  it('has status PENDING if a promise has been returned but has not yet resolved', () => {
    const spy = jasmine.createSpy('onChange');
    const deferred = defer();
    mount(<Component promiseFactory={() => deferred.promise} deps={[]} onChange={spy} />);
    expect(spy).toHaveBeenCalledTimes(2);

    const result: IUseLatestPromiseResult<any> = spy.calls.mostRecent().args[0];
    expect(promiseState(result)).toEqual({ status: 'PENDING', result: undefined, error: undefined, requestId: 0 });
  });

  it('has status RESOLVED if a promise resolved', async () => {
    const spy = jasmine.createSpy('onChange');
    const deferred = defer();
    const component = mount(<Component promiseFactory={() => deferred.promise} deps={[]} onChange={spy} />);
    expect(spy).toHaveBeenCalledTimes(2);

    deferred.resolve('payload');
    await deferred.promise;
    component.setProps({});

    expect(spy).toHaveBeenCalledTimes(3);
    const result: IUseLatestPromiseResult<any> = spy.calls.mostRecent().args[0];
    expect(promiseState(result)).toEqual({ status: 'RESOLVED', result: 'payload', error: undefined, requestId: 0 });
  });

  it('has status REJECTED if a promise rejected', async () => {
    const spy = jasmine.createSpy('onChange');
    const deferred = defer();
    const component = mount(<Component promiseFactory={() => deferred.promise} deps={[]} onChange={spy} />);
    expect(spy).toHaveBeenCalledTimes(2);

    deferred.reject('error');
    try {
      await deferred.promise;
    } catch (error) {}
    component.setProps({});

    expect(spy).toHaveBeenCalledTimes(3);
    const result: IUseLatestPromiseResult<any> = spy.calls.mostRecent().args[0];
    expect(promiseState(result)).toEqual({ status: 'REJECTED', result: undefined, error: 'error', requestId: 0 });
  });

  it('only handles the latest promise when multiple promises are pending', async () => {
    const spy = jasmine.createSpy('onChange');
    const deferred1 = defer();
    const component = mount(<Component promiseFactory={() => deferred1.promise} deps={[1]} onChange={spy} />);
    expect(spy).toHaveBeenCalledTimes(2);

    const deferred2 = defer();
    component.setProps({ promiseFactory: () => deferred2.promise, deps: [2] });
    component.setProps({});
    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.calls.mostRecent().args[0].status).toEqual('PENDING');

    deferred1.resolve('payload1');
    await deferred1.promise;
    component.setProps({});

    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.calls.mostRecent().args[0].status).toEqual('PENDING');

    deferred2.resolve('payload2');
    await deferred2.promise;
    component.setProps({});

    expect(spy).toHaveBeenCalledTimes(4);
    const result: IUseLatestPromiseResult<any> = spy.calls.mostRecent().args[0];
    expect(promiseState(result)).toEqual({ status: 'RESOLVED', result: 'payload2', error: undefined, requestId: 1 });
  });

  it('gets a new promise if refresh() is called', async () => {
    const spy = jasmine.createSpy('onChange');
    const deferred = defer();
    const promiseFactorySpy = jasmine.createSpy('promiseFactory').and.callFake(() => deferred.promise);
    const component = mount(<Component promiseFactory={promiseFactorySpy} deps={[]} onChange={spy} />);
    expect(promiseFactorySpy).toHaveBeenCalledTimes(1);

    // initial promise is resolved.
    deferred.resolve('payload');
    await deferred.promise;
    component.setProps({});

    spy.calls.mostRecent().args[0].refresh();
    component.setProps({});
    expect(promiseFactorySpy).toHaveBeenCalledTimes(2);
  });

  it('ignores old pending results if a newer promise is being processed', async () => {
    const spy = jasmine.createSpy('onChange');
    const deferred1 = defer();
    const deferred2 = defer();
    const component = mount(<Component promiseFactory={() => deferred1.promise} deps={[1]} onChange={spy} />);

    component.setProps({ promiseFactory: () => deferred2.promise, deps: [2] });

    // The first promise is resolved.
    deferred1.resolve('payload1');
    await deferred1.promise;
    component.setProps({});

    // The second promise is resolved.
    deferred2.resolve('payload2');
    await deferred2.promise;
    component.setProps({});

    expect(spy).toHaveBeenCalledTimes(4);
    const allCalls = spy.calls.allArgs().map((args) => promiseState(args[0]));
    expect(allCalls[0]).toEqual({ status: 'NONE', result: undefined, error: undefined, requestId: 0 });
    // initial request
    expect(allCalls[1]).toEqual({ status: 'PENDING', result: undefined, error: undefined, requestId: 0 });
    // second request
    expect(allCalls[2]).toEqual({ status: 'PENDING', result: undefined, error: undefined, requestId: 1 });
    // resolved second request
    expect(allCalls[3]).toEqual({ status: 'RESOLVED', result: 'payload2', error: undefined, requestId: 1 });
  });
});

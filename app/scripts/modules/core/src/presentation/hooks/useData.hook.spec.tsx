import React from 'react';
import { mount } from 'enzyme';
import { IUseLatestPromiseResult } from './useLatestPromise.hook';
import { useData } from './useData.hook';

describe('useData hook', () => {
  // Remove the the refresh function for .isEqual assertions
  function promiseState(call: IUseLatestPromiseResult<any>) {
    const { refresh, ...state } = call;
    return state;
  }

  function Component(props: any) {
    const { promiseFactory, deps, onChange, defaultValue } = props;
    const useDataResult: IUseLatestPromiseResult<any> = useData(promiseFactory, defaultValue, deps);
    const { status, result, error, requestId } = useDataResult;

    React.useEffect(() => onChange(useDataResult), [status, result, error, requestId]);
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

  it('the factory is not called when some deps are null', () => {
    const spy = jasmine.createSpy('onChange');
    const factory = jasmine.createSpy('factory');
    mount(<Component promiseFactory={factory} deps={['foo', null]} defaultValue={'default'} onChange={spy} />);
    expect(factory).toHaveBeenCalledTimes(0);
  });

  it('the factory is not called when some deps are undefined', () => {
    const spy = jasmine.createSpy('onChange');
    const factory = jasmine.createSpy('factory');
    mount(<Component promiseFactory={factory} deps={['foo', undefined]} defaultValue={'default'} onChange={spy} />);
    expect(factory).toHaveBeenCalledTimes(0);
  });

  it('the default result is returned until the promise resolves', async done => {
    const spy = jasmine.createSpy('onChange');
    const deferred = defer();
    const factory = () => deferred.promise;
    const component = mount(
      <Component promiseFactory={factory} deps={['foo']} defaultValue={'default'} onChange={spy} />,
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

    deferred.resolve('result');
    await deferred.promise;
    component.setProps({});
    expect(spy).toHaveBeenCalledTimes(3);
    expect(promiseState(spy.calls.argsFor(2)[0])).toEqual({
      status: 'RESOLVED',
      result: 'result',
      error: undefined,
      requestId: 0,
    });

    done();
  });
});

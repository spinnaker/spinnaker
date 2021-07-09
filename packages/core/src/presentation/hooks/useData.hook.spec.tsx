import React from 'react';
import { mount } from 'enzyme';
import { IUseLatestPromiseResult } from './useLatestPromise.hook';
import { useData } from './useData.hook';

describe('useData hook', () => {
  function TestComponent(props: any) {
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
    mount(<TestComponent promiseFactory={factory} deps={['foo', null]} defaultValue={'default'} onChange={spy} />);
    expect(factory).toHaveBeenCalledTimes(0);
  });

  it('the factory is not called when some deps are undefined', () => {
    const spy = jasmine.createSpy('onChange');
    const factory = jasmine.createSpy('factory');
    mount(<TestComponent promiseFactory={factory} deps={['foo', undefined]} defaultValue={'default'} onChange={spy} />);
    expect(factory).toHaveBeenCalledTimes(0);
  });

  it('the default result is returned until the promise resolves', async () => {
    const spy = jasmine.createSpy('onChange');
    const deferred = defer();
    const factory = () => deferred.promise;
    const wrapper = mount(
      <TestComponent promiseFactory={factory} deps={['foo']} defaultValue={'default'} onChange={spy} />,
    );

    expect(spy).toHaveBeenCalledTimes(2);

    expect(spy.calls.argsFor(0)[0]).toEqual({
      status: 'NONE',
      result: 'default',
      error: undefined,
      requestId: 0,
      refresh: jasmine.any(Function),
    });

    expect(spy.calls.argsFor(1)[0]).toEqual({
      status: 'PENDING',
      result: 'default',
      error: undefined,
      requestId: 0,
      refresh: jasmine.any(Function),
    });

    deferred.resolve('result');
    await deferred.promise;
    wrapper.setProps({});
    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.calls.argsFor(2)[0]).toEqual({
      status: 'RESOLVED',
      result: 'result',
      error: undefined,
      requestId: 0,
      refresh: jasmine.any(Function),
    });
  });

  it('the default result is returned until the first result is seen (even if deps are falsey)', async () => {
    const spy = jasmine.createSpy('onChange');
    const deferred = defer();
    const factory = () => deferred.promise;
    const wrapper = mount(
      <TestComponent promiseFactory={factory} deps={[null]} defaultValue={'default'} onChange={spy} />,
    );

    expect(spy).toHaveBeenCalledTimes(1);

    expect(spy.calls.argsFor(0)[0]).toEqual({
      status: 'NONE',
      result: 'default',
      error: undefined,
      requestId: 0,
      refresh: jasmine.any(Function),
    });

    // Why twice? Somehow the onChange useEffect isn't running unless you run this multiple times
    wrapper.setProps({ deps: ['foo'] });
    wrapper.setProps({ deps: ['foo'] });

    expect(spy).toHaveBeenCalledTimes(2);

    expect(spy.calls.argsFor(1)[0]).toEqual({
      status: 'PENDING',
      result: 'default',
      error: undefined,
      requestId: 1,
      refresh: jasmine.any(Function),
    });

    deferred.resolve('result');
    await deferred.promise;

    wrapper.setProps({});
    expect(spy).toHaveBeenCalledTimes(3);
    expect(spy.calls.argsFor(2)[0]).toEqual({
      status: 'RESOLVED',
      result: 'result',
      error: undefined,
      requestId: 1,
      refresh: jasmine.any(Function),
    });
  });
});

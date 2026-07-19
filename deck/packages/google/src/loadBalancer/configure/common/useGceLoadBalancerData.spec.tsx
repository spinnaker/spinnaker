import React from 'react';
import { mount } from 'enzyme';

import type { IGceLoadBalancerDataReaders, IGceLoadBalancerDataState } from './gceLoadBalancerData';
import { useGceLoadBalancerData } from './useGceLoadBalancerData';

describe('useGceLoadBalancerData', () => {
  it('reloads data when the account changes', async () => {
    const readers = testReaders();
    const states: IGceLoadBalancerDataState[] = [];
    const wrapper = mount(<Harness account="first" readers={readers} onState={(state) => states.push(state)} />);

    await settle();
    wrapper.setProps({ account: 'second' });
    await settle();

    expect(readers.regions.calls.allArgs()).toEqual([['first'], ['second']]);
    expect(states[states.length - 1].status).toBe('ready');
    wrapper.unmount();
  });

  it('does not publish a request result after unmount', async () => {
    const regions = deferred<unknown[]>();
    const readers = testReaders();
    readers.regions.and.returnValue(regions.promise);
    const onState = jasmine.createSpy('onState');
    const wrapper = mount(<Harness account="test-account" readers={readers} onState={onState} />);
    await settle();
    const callsBeforeUnmount = onState.calls.count();

    wrapper.unmount();
    regions.resolve([{ name: 'late-region' }]);
    await settle();

    expect(onState.calls.count()).toBe(callsBeforeUnmount);
  });
});

function Harness({
  account,
  readers,
  onState,
}: {
  account: string;
  readers: IGceLoadBalancerDataReaders;
  onState: (state: IGceLoadBalancerDataState) => void;
}) {
  const state = useGceLoadBalancerData(account, readers);
  React.useEffect(() => {
    onState(state);
  }, [state.status, state.data, state.error]);
  return null;
}

function testReaders(): jasmine.SpyObj<IGceLoadBalancerDataReaders> {
  return jasmine.createSpyObj<IGceLoadBalancerDataReaders>('readers', {
    accounts: Promise.resolve([]),
    addresses: Promise.resolve([]),
    backendServices: Promise.resolve([]),
    certificates: Promise.resolve([]),
    healthChecks: Promise.resolve([]),
    networks: Promise.resolve([]),
    regions: Promise.resolve([]),
    subnets: Promise.resolve([]),
  });
}

function deferred<T>() {
  let resolve: (value: T) => void;
  const promise = new Promise<T>((resolver) => (resolve = resolver));
  return { promise, resolve: resolve! };
}

async function settle(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await new Promise<void>((resolve) => setTimeout(resolve, 0));
}

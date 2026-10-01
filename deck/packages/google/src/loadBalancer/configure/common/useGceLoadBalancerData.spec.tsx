import type { Mocked } from 'vitest';
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

    expect(readers.regions.mock.calls).toEqual([['first'], ['second']]);
    expect(states[states.length - 1].status).toBe('ready');
    wrapper.unmount();
  });

  it('does not publish a request result after unmount', async () => {
    const regions = deferred<unknown[]>();
    const readers = testReaders();
    readers.regions.mockReturnValue(regions.promise);
    const onState = vi.fn();
    const wrapper = mount(<Harness account="test-account" readers={readers} onState={onState} />);
    await settle();
    const callsBeforeUnmount = onState.mock.calls.length;

    wrapper.unmount();
    regions.resolve([{ name: 'late-region' }]);
    await settle();

    expect(onState.mock.calls.length).toBe(callsBeforeUnmount);
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

function testReaders(): Mocked<IGceLoadBalancerDataReaders> {
  return {
    accounts: vi.fn().mockReturnValue(Promise.resolve([])),
    addresses: vi.fn().mockReturnValue(Promise.resolve([])),
    backendServices: vi.fn().mockReturnValue(Promise.resolve([])),
    certificates: vi.fn().mockReturnValue(Promise.resolve([])),
    healthChecks: vi.fn().mockReturnValue(Promise.resolve([])),
    networks: vi.fn().mockReturnValue(Promise.resolve([])),
    regions: vi.fn().mockReturnValue(Promise.resolve([])),
    subnets: vi.fn().mockReturnValue(Promise.resolve([])),
  };
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

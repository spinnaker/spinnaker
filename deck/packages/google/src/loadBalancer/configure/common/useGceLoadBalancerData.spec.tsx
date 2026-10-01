import type { Mocked } from 'vitest';
import { act } from '@testing-library/react';

import { renderHookHarness } from '../../../../../core/src/utils/testUtils/hookHarness';
import type { IGceLoadBalancerDataReaders } from './gceLoadBalancerData';
import { useGceLoadBalancerData } from './useGceLoadBalancerData';

describe('useGceLoadBalancerData', () => {
  it('reloads data when the account changes', async () => {
    const readers = testReaders();
    const hook = renderHookHarness(
      ({ account, dataReaders }: { account: string; dataReaders: IGceLoadBalancerDataReaders }) =>
        useGceLoadBalancerData(account, dataReaders),
      { account: 'first', dataReaders: readers },
    );

    await act(settle);
    hook.rerenderHook({ account: 'second', dataReaders: readers });
    await act(settle);

    expect(readers.regions.mock.calls).toEqual([['first'], ['second']]);
    expect(hook.result.current.status).toBe('ready');
    hook.unmount();
  });

  it('does not publish a request result after unmount', async () => {
    const regions = deferred<unknown[]>();
    const readers = testReaders();
    readers.regions.mockReturnValue(regions.promise);
    const hook = renderHookHarness(
      ({ account, dataReaders }: { account: string; dataReaders: IGceLoadBalancerDataReaders }) =>
        useGceLoadBalancerData(account, dataReaders),
      { account: 'test-account', dataReaders: readers },
    );
    await act(settle);
    const stateBeforeUnmount = hook.result.current;

    hook.unmount();
    regions.resolve([{ name: 'late-region' }]);
    await act(settle);

    expect(hook.result.current).toBe(stateBeforeUnmount);
  });
});

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

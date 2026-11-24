import { waitFor } from '@testing-library/react';
import { BehaviorSubject } from 'rxjs';

import { renderHookHarness } from '../../../../core/src/utils/testUtils/hookHarness';

import { useOracleLoadBalancerDetails } from './OracleLoadBalancerDetails';

describe('useOracleLoadBalancerDetails', () => {
  function renderHook(status: any, appOverrides: any = {}) {
    const dataSource = {
      status$: new BehaviorSubject(status),
      refresh: vi.fn(),
    };
    const app = {
      getDataSource: vi.fn().mockReturnValue(dataSource),
      ...appOverrides,
    };
    const props = {
      app,
      loadBalancerParams: { name: 'my-lb', region: 'us-phoenix-1', accountId: 'oracle-account' },
      autoClose: vi.fn(),
    };

    const hook = renderHookHarness(useOracleLoadBalancerDetails, props);
    return { ...hook, dataSource, props };
  }

  it('waits for load balancers to load before reporting not found', () => {
    const { result, props } = renderHook({ status: 'NOT_INITIALIZED', loaded: false, data: [], lastRefresh: 0 });

    expect(result.current.loading).toBe(true);
    expect(result.current.error).toBeUndefined();
    expect(props.autoClose).not.toHaveBeenCalled();
  });

  it('closes after loaded load balancers do not contain the requested load balancer', async () => {
    const { result, props } = renderHook({ status: 'FETCHED', loaded: true, data: [], lastRefresh: 1 });

    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBe('Load balancer not found');
    await waitFor(() => expect(props.autoClose).toHaveBeenCalled());
  });
});

import { act, waitFor } from '@testing-library/react';
import React from 'react';
import { BehaviorSubject } from 'rxjs';

import { DeckRuntimeContext } from '@spinnaker/core';
import { renderHookHarness } from '../../../../core/src/utils/testUtils/hookHarness';

import { useAmazonLoadBalancerDetails } from './amazonLoadBalancerDetails';
import { RequestBuilder } from '../../../../core/src/api/ApiService';

describe('useAmazonLoadBalancerDetails', () => {
  const defaultHttpClient = RequestBuilder.defaultHttpClient;
  let runtimeServices: any;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>{children}</DeckRuntimeContext.Provider>
  );

  beforeEach(() => {
    runtimeServices = {};
  });

  afterEach(() => {
    RequestBuilder.defaultHttpClient = defaultHttpClient;
  });

  it('does not refetch details when an unrelated state update re-renders with a new autoClose callback', async () => {
    const loadBalancer = {
      account: 'test',
      name: 'frontend',
      provider: 'aws',
      region: 'us-east-1',
      loadBalancerType: 'classic',
      subnets: [],
    } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [loadBalancer],
    });
    const app = {
      getDataSource: vi.fn().mockReturnValue({
        status$,
        refresh: vi.fn(),
      }),
    } as any;
    const get = vi.fn().mockReturnValue(new Promise(() => undefined));
    RequestBuilder.defaultHttpClient = { get } as any;
    runtimeServices.securityGroupReader = {
      getApplicationSecurityGroup: vi.fn(),
    };

    const props = {
      app,
      loadBalancerParams: { accountId: 'test', name: 'frontend', provider: 'aws', region: 'us-east-1' },
      autoClose: () => undefined,
    } as any;
    const hook = renderHookHarness(useAmazonLoadBalancerDetails, props, { wrapper: RuntimeWrapper });
    await waitFor(() => expect(get).toHaveBeenCalledTimes(1));
    hook.rerenderHook({ ...props, autoClose: () => undefined });

    expect(get).toHaveBeenCalledTimes(1);
  });

  it('does not update state after unmount when details loading finishes', async () => {
    const loadBalancer = {
      account: 'test',
      name: 'frontend',
      provider: 'aws',
      region: 'us-east-1',
      loadBalancerType: 'classic',
      subnets: [],
    } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [loadBalancer],
    });
    const app = {
      getDataSource: vi.fn().mockReturnValue({
        status$,
        refresh: vi.fn(),
      }),
    } as any;
    let resolveDetails: (details: any[]) => void = () => undefined;
    const detailsRequest = new Promise<any[]>((resolve) => {
      resolveDetails = resolve;
    });
    const get = vi.fn().mockReturnValue(detailsRequest);
    const consoleError = vi.spyOn(console, 'error').mockReturnValue(undefined);
    RequestBuilder.defaultHttpClient = { get } as any;
    runtimeServices.securityGroupReader = {
      getApplicationSecurityGroup: vi.fn(),
    };

    const hook = renderHookHarness(
      useAmazonLoadBalancerDetails,
      {
        app,
        loadBalancerParams: { accountId: 'test', name: 'frontend', provider: 'aws', region: 'us-east-1' },
        autoClose: () => undefined,
      } as any,
      { wrapper: RuntimeWrapper },
    );
    await waitFor(() => expect(get).toHaveBeenCalledTimes(1));
    hook.unmount();

    await act(async () => {
      resolveDetails([]);
      await detailsRequest;
    });

    const unmountedStateUpdateWarning = consoleError.mock.calls.some(([message]) =>
      String(message).includes('unmounted component'),
    );
    expect(unmountedStateUpdateWarning).toBe(false);
  });
});

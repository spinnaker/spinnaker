import { UISref } from '@uirouter/react';
import { UIRouterReact } from '@uirouter/react';
import React from 'react';
import { act, screen, waitFor } from '@testing-library/react';
import { BehaviorSubject } from 'rxjs';

import { createDeckRuntime, DeckRuntimeContext, LoadBalancerReader } from '@spinnaker/core';
import { renderHookHarness } from '../../../../core/src/utils/testUtils/hookHarness';
import { renderWithRouter } from '../../../../core/src/utils/testUtils/rtl';

import {
  AzureLoadBalancerDetailsSection,
  AzureLoadBalancerFirewallsSection,
  azureLoadBalancerDetailsSections,
  loadAzureLoadBalancerDetails,
  useAzureLoadBalancerDetails,
} from './azureLoadBalancerDetails';

describe('AzureLoadBalancerDetails', () => {
  let runtime: ReturnType<typeof createDeckRuntime>;
  const RuntimeWrapper = ({ children }: React.PropsWithChildren<{}>) => (
    <DeckRuntimeContext.Provider value={runtime}>{children}</DeckRuntimeContext.Provider>
  );

  beforeEach(() => {
    runtime = createDeckRuntime(new UIRouterReact());
  });

  afterEach(() => {
    runtime.dispose();
  });

  function buildApp(loadBalancers: any[]) {
    return {
      loadBalancers: {
        data: loadBalancers,
      },
    } as any;
  }

  const params = {
    name: 'fnord-frontend',
    accountId: 'test-account',
    region: 'westus',
    provider: 'azure',
  } as any;

  function deferred<T>() {
    let resolve: (value: T) => void;
    const promise = new Promise<T>((promiseResolve) => {
      resolve = promiseResolve;
    });
    return { promise, resolve: resolve! };
  }

  function findElement(root: React.ReactNode, type: React.ElementType): React.ReactElement<any> | undefined {
    if (!React.isValidElement(root)) {
      return undefined;
    }
    if (root.type === type) {
      return root;
    }
    return React.Children.toArray(root.props.children)
      .map((child) => findElement(child, type))
      .find(Boolean);
  }

  it('does not refetch details when a rerender recreates callbacks and route params', async () => {
    const summary = {
      account: 'test-account',
      name: 'fnord-frontend',
      provider: 'azure',
      region: 'westus',
    } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [summary],
    });
    const app = {
      getDataSource: vi.fn().mockReturnValue({
        status$,
        refresh: vi.fn(),
      }),
    } as any;
    const getLoadBalancerDetails = vi
      .spyOn(LoadBalancerReader.prototype, 'getLoadBalancerDetails')
      .mockReturnValue(new Promise(() => undefined));

    const hook = renderHookHarness(
      useAzureLoadBalancerDetails,
      { app, loadBalancerParams: { ...params }, autoClose: () => undefined } as any,
      { wrapper: RuntimeWrapper },
    );
    await waitFor(() => expect(getLoadBalancerDetails).toHaveBeenCalledTimes(1));
    hook.rerenderHook({ app, loadBalancerParams: { ...params }, autoClose: () => undefined } as any);

    expect(getLoadBalancerDetails).toHaveBeenCalledTimes(1);
  });

  it('does not let an older route request overwrite newer load balancer details', async () => {
    const oldSummary = { account: 'test-account', name: 'old-lb', provider: 'azure', region: 'westus' } as any;
    const newSummary = { account: 'test-account', name: 'new-lb', provider: 'azure', region: 'westus' } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [oldSummary, newSummary],
    });
    const app = {
      getDataSource: vi.fn().mockReturnValue({
        status$,
        refresh: vi.fn(),
      }),
    } as any;
    const oldRequest = deferred<any[]>();
    const newRequest = deferred<any[]>();
    const getLoadBalancerDetails = vi
      .spyOn(LoadBalancerReader.prototype, 'getLoadBalancerDetails')
      .mockReturnValueOnce(oldRequest.promise)
      .mockReturnValueOnce(newRequest.promise);

    const hook = renderHookHarness(
      useAzureLoadBalancerDetails,
      { app, loadBalancerParams: { ...params, name: 'old-lb' }, autoClose: () => undefined } as any,
      { wrapper: RuntimeWrapper },
    );
    await waitFor(() => expect(getLoadBalancerDetails).toHaveBeenCalledTimes(1));
    hook.rerenderHook({
      app,
      loadBalancerParams: { ...params, name: 'new-lb' },
      autoClose: () => undefined,
    } as any);

    await waitFor(() => expect(getLoadBalancerDetails).toHaveBeenCalledTimes(2));

    await act(async () => {
      newRequest.resolve([{ name: 'new-lb' }]);
      await newRequest.promise;
    });
    expect(hook.result.current.data?.name).toBe('new-lb');

    await act(async () => {
      oldRequest.resolve([{ name: 'old-lb' }]);
      await oldRequest.promise;
    });
    expect(hook.result.current.data?.name).toBe('new-lb');
  });

  it('stops loading when a data source error invalidates an active details request', async () => {
    const summary = { account: 'test-account', name: 'fnord-frontend', provider: 'azure', region: 'westus' } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [summary],
      error: null,
    });
    const app = {
      getDataSource: vi.fn().mockReturnValue({
        status$,
        refresh: vi.fn(),
      }),
    } as any;
    const request = deferred<any[]>();
    const getLoadBalancerDetails = vi
      .spyOn(LoadBalancerReader.prototype, 'getLoadBalancerDetails')
      .mockReturnValue(request.promise);

    const hook = renderHookHarness(
      useAzureLoadBalancerDetails,
      { app, loadBalancerParams: params, autoClose: () => undefined } as any,
      { wrapper: RuntimeWrapper },
    );
    await waitFor(() => expect(getLoadBalancerDetails).toHaveBeenCalledTimes(1));
    expect(hook.result.current.loading).toBe(true);

    await act(async () => {
      status$.next({
        status: 'ERROR',
        loaded: true,
        lastRefresh: 2,
        data: [summary],
        error: new Error('load balancer data source failed'),
      });
      await Promise.resolve();
    });
    expect(hook.result.current.loading).toBe(false);

    await act(async () => {
      request.resolve([{ name: 'fnord-frontend' }]);
      await request.promise;
    });
    expect(hook.result.current.data).toBeUndefined();
  });

  it('does not update state when a details request resolves after unmount', async () => {
    const summary = { account: 'test-account', name: 'fnord-frontend', provider: 'azure', region: 'westus' } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [summary],
    });
    const app = {
      getDataSource: vi.fn().mockReturnValue({
        status$,
        refresh: vi.fn(),
      }),
    } as any;
    const request = deferred<any[]>();
    vi.spyOn(LoadBalancerReader.prototype, 'getLoadBalancerDetails').mockReturnValue(request.promise);

    const hook = renderHookHarness(
      useAzureLoadBalancerDetails,
      { app, loadBalancerParams: params, autoClose: () => undefined } as any,
      { wrapper: RuntimeWrapper },
    );
    await waitFor(() => expect(LoadBalancerReader.prototype.getLoadBalancerDetails).toHaveBeenCalled());
    const consoleError = vi.spyOn(console, 'error').mockReturnValue(undefined);
    hook.unmount();

    await act(async () => {
      request.resolve([{ name: 'fnord-frontend' }]);
      await request.promise;
    });

    const errors = consoleError.mock.calls.flat().map(String).join(' ');
    expect(errors).not.toContain('state update on an unmounted component');
  });

  it('loads matching summary details and preserves legacy Azure detail fields', async () => {
    const summary = {
      account: 'test-account',
      name: 'fnord-frontend',
      provider: 'azure',
      region: 'westus',
      loadBalancerType: 'APPLICATION_GATEWAY',
      serverGroups: [],
    };
    const otherSummary = { ...summary, account: 'other-account' };
    const details = [
      { name: 'other-lb', securityGroups: ['sg-other'] },
      {
        name: 'fnord-frontend',
        createdTime: 1710000000000,
        dnsName: 'fnord.example.com',
        securityGroups: ['sg-2', 'sg-1'],
      },
    ];
    const loadBalancerReader = {
      getLoadBalancerDetails: vi.fn().mockReturnValue(Promise.resolve(details)),
    };
    const securityGroupReader = {
      getApplicationSecurityGroup: vi.fn().mockImplementation(
        (_app: any, account: string, region: string, id: string) =>
          ({
            'sg-1': { id: 'sg-1', name: 'z-firewall', account, region },
            'sg-2': { id: 'sg-2', name: 'a-firewall', account, region },
          }[id]),
      ),
    };
    const autoClose = vi.fn();

    const loadBalancer = await loadAzureLoadBalancerDetails({
      app: buildApp([otherSummary, summary]),
      loadBalancerParams: params,
      loadBalancerReader: loadBalancerReader as any,
      securityGroupReader: securityGroupReader as any,
      autoClose,
    });

    expect(loadBalancerReader.getLoadBalancerDetails).toHaveBeenCalledWith(
      'azure',
      'test-account',
      'westus',
      'fnord-frontend',
    );
    expect(securityGroupReader.getApplicationSecurityGroup).toHaveBeenCalledWith(
      expect.anything(),
      'test-account',
      'westus',
      'sg-2',
    );
    expect(autoClose).not.toHaveBeenCalled();
    expect(loadBalancer).toBe(summary as any);
    expect(loadBalancer.elb).toBe(details[1] as any);
    expect(loadBalancer.account).toBe('test-account');
    expect(loadBalancer.loadBalancerType).toBe('Application Gateway');
    expect(loadBalancer.securityGroups).toEqual([
      { id: 'sg-2', name: 'a-firewall', account: 'test-account', region: 'westus' },
      { id: 'sg-1', name: 'z-firewall', account: 'test-account', region: 'westus' },
    ] as any);
  });

  it('uses the load balancer and security group readers from the runtime provider', async () => {
    const summary = {
      account: 'test-account',
      name: 'fnord-frontend',
      provider: 'azure',
      region: 'westus',
    } as any;
    const status$ = new BehaviorSubject({
      status: 'FETCHED',
      loaded: true,
      lastRefresh: 1,
      data: [summary],
    });
    const app = {
      getDataSource: vi.fn().mockReturnValue({
        status$,
        refresh: vi.fn(),
      }),
      loadBalancers: { data: [summary] },
    } as any;
    const getLoadBalancerDetails = vi
      .spyOn(LoadBalancerReader.prototype, 'getLoadBalancerDetails')
      .mockResolvedValue([{ name: 'fnord-frontend', securityGroups: ['firewall-id'] }] as any);
    const getApplicationSecurityGroup = vi
      .spyOn(runtime.services.securityGroupReader, 'getApplicationSecurityGroup')
      .mockReturnValue({ id: 'firewall-id', name: 'firewall' } as any);

    const hook = renderHookHarness(
      useAzureLoadBalancerDetails,
      { app, loadBalancerParams: params, autoClose: () => undefined } as any,
      { wrapper: RuntimeWrapper },
    );
    await waitFor(() => expect(getApplicationSecurityGroup).toHaveBeenCalled());

    expect(getLoadBalancerDetails.mock.instances.at(-1)).toBe(runtime.services.loadBalancerReader);
    expect(getApplicationSecurityGroup).toHaveBeenCalledWith(app, 'test-account', 'westus', 'firewall-id');
    hook.unmount();
  });

  it('closes the details panel when no matching summary exists', async () => {
    const loadBalancerReader = {
      getLoadBalancerDetails: vi.fn(),
    };
    const autoClose = vi.fn();

    const loadBalancer = await loadAzureLoadBalancerDetails({
      app: buildApp([{ name: 'fnord-frontend', account: 'test-account', region: 'eastus', provider: 'azure' }]),
      loadBalancerParams: params,
      loadBalancerReader: loadBalancerReader as any,
      securityGroupReader: {} as any,
      autoClose,
    });

    expect(loadBalancer).toBeUndefined();
    expect(autoClose).toHaveBeenCalled();
    expect(loadBalancerReader.getLoadBalancerDetails).not.toHaveBeenCalled();
  });

  it('loads matching details from fresh load balancer data instead of stale app data', async () => {
    const staleSummary = { name: 'fnord-frontend', account: 'test-account', region: 'eastus', provider: 'azure' };
    const freshSummary = { name: 'fnord-frontend', account: 'test-account', region: 'westus', provider: 'azure' };
    const details = [{ name: 'fnord-frontend' }];
    const loadBalancerReader = {
      getLoadBalancerDetails: vi.fn().mockReturnValue(Promise.resolve(details)),
    };
    const autoClose = vi.fn();

    const loadBalancer = await loadAzureLoadBalancerDetails({
      app: buildApp([staleSummary]),
      loadBalancers: [freshSummary],
      loadBalancerParams: params,
      loadBalancerReader: loadBalancerReader as any,
      securityGroupReader: {} as any,
      autoClose,
    } as any);

    expect(loadBalancer).toBe(freshSummary as any);
    expect(autoClose).not.toHaveBeenCalled();
    expect(loadBalancerReader.getLoadBalancerDetails).toHaveBeenCalledWith(
      'azure',
      'test-account',
      'westus',
      'fnord-frontend',
    );
  });

  it('registers health checks separately from listeners', () => {
    expect(azureLoadBalancerDetailsSections.map((Section) => Section.name)).toEqual([
      'AzureLoadBalancerDetailsSection',
      'AzureLoadBalancerStatusSection',
      'AzureLoadBalancerListenersSection',
      'AzureLoadBalancerFirewallsSection',
      'AzureLoadBalancerHealthChecksSection',
    ]);
  });

  it('renders legacy server group navigation links', () => {
    const loadBalancer = {
      account: 'test-account',
      loadBalancerType: 'Azure Load Balancer',
      region: 'westus',
      serverGroups: [{ name: 'fnord-v001', account: 'test-account', region: 'westus', isDisabled: false }],
    };

    const link = findElement(AzureLoadBalancerDetailsSection({ loadBalancer: loadBalancer as any }), UISref);

    expect(link?.props.to).toBe('^.serverGroup');
    expect(link?.props.params).toEqual({
      region: 'westus',
      accountId: 'test-account',
      serverGroup: 'fnord-v001',
      provider: 'azure',
    });
  });

  it('renders legacy firewall navigation links', () => {
    const loadBalancer = {
      account: 'test-account',
      provider: 'azure',
      region: 'westus',
      vpcId: 'vnet-1',
      securityGroups: [{ id: 'sg-1', name: 'frontend-firewall' }],
    };

    renderWithRouter(
      <AzureLoadBalancerFirewallsSection loadBalancer={{ ...loadBalancer, securityGroups: [] } as any} />,
    );
    expect(screen.getByRole('heading', { name: 'Firewalls' })).toBeInTheDocument();
    const link = findElement(AzureLoadBalancerFirewallsSection({ loadBalancer: loadBalancer as any }), UISref);

    expect(link?.props.to).toBe('^.firewallDetails');
    expect(link?.props.params).toEqual({
      name: 'frontend-firewall',
      accountId: 'test-account',
      region: 'westus',
      vpcId: 'vnet-1',
      provider: 'azure',
    });
  });
});

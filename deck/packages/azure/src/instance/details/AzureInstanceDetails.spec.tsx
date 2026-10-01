import type { Mock } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import {
  hashLocationPlugin,
  servicesPlugin,
  UIRouterContext,
  UIRouterReact,
  UISref,
  UIViewContext,
} from '@uirouter/react';
import React from 'react';

import {
  AccountService,
  CloudProviderRegistry,
  ConfirmationModalService,
  InstanceReader,
  InstanceWriter,
} from '@spinnaker/core';

import {
  AzureInstanceActionsComponent as AzureInstanceActions,
  AzureInstanceDetails as RoutedAzureInstanceDetails,
  AzureInstanceDetailsComponent as AzureInstanceDetails,
  AzureInstanceInformationSection,
  loadAzureInstanceDetails,
} from './AzureInstanceDetails';
import { registerAzureProvider } from '../../azure.module';

describe('AzureInstanceDetails', () => {
  let router: UIRouterReact;
  const stateService = { go: vi.fn(), includes: vi.fn().mockReturnValue(true) };
  const routerProps = { router: {} as any, stateParams: {}, stateService: stateService as any };
  const instanceParams = {
    account: 'test-account',
    instanceId: 'i-123',
    provider: 'azure',
    region: 'westus',
  } as any;

  function app(serverGroups: any[] = [], loadBalancers: any[] = []) {
    return {
      isStandalone: false,
      loadBalancers: {
        data: loadBalancers,
        ready: () => Promise.resolve(),
        onRefresh: () => vi.fn(),
      },
      serverGroups: {
        data: serverGroups,
        ready: () => Promise.resolve(),
        onRefresh: () => vi.fn(),
      },
    } as any;
  }

  function details(overrides: any = {}) {
    return {
      instanceId: 'i-123',
      instanceType: 'Standard_D2_v2',
      launchTime: 1710000000000,
      privateIpAddress: '10.0.0.4',
      publicDnsName: 'i-123.example.com',
      health: [{ type: 'Discovery', state: 'Up', vipAddress: 'vip-a,vip-b', extra: 'from details' }],
      ...overrides,
    } as any;
  }

  function serverGroup(overrides: any = {}) {
    return {
      account: 'test-account',
      loadBalancers: ['lb-1'],
      name: 'fnord-v001',
      region: 'westus',
      vpcId: 'vnet-1',
      instances: [
        {
          id: 'i-123',
          health: [{ type: 'Discovery', state: 'Down', reason: 'summary reason' }],
          healthState: 'Down',
          instanceType: 'summary-type',
        },
      ],
      ...overrides,
    } as any;
  }

  async function load(appFixture: any, params: any = instanceParams, fetchedDetails: any = details()) {
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(Promise.resolve(fetchedDetails));
    return loadAzureInstanceDetails({ app: appFixture, instance: params });
  }

  function actionLabels(instance: any): string[] {
    const rendered = render(<AzureInstanceActions {...routerProps} app={app()} instance={instance} />);
    fireEvent.click(screen.getByRole('button', { name: 'Instance Actions' }));
    const labels = screen.getAllByRole('menuitem').map((item) => String(item.textContent).trim());
    rendered.unmount();
    return labels;
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

  function renderRouted(component: React.ReactElement) {
    const routed = (child: React.ReactElement) => (
      <UIRouterContext.Provider value={router}>
        <UIViewContext.Provider
          value={{ fqn: 'application.instance', context: router.stateRegistry.get('application.instance') as any }}
        >
          {child}
        </UIViewContext.Provider>
      </UIRouterContext.Provider>
    );
    const rendered = render(routed(component));
    return { ...rendered, rerenderRouted: (child: React.ReactElement) => rendered.rerender(routed(child)) };
  }

  beforeEach(() => {
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockResolvedValue(false);
    router = new UIRouterReact();
    router.plugin(servicesPlugin);
    router.plugin(hashLocationPlugin);
    ['application', 'application.instance', 'application.serverGroup'].forEach((name) =>
      router.stateRegistry.register({ name, url: `/${name.split('.').pop()}` }),
    );
  });

  afterEach(() => router.dispose());

  it('loads details for instances found in app.serverGroups.data', async () => {
    const instance = await load(app([serverGroup()]));

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('test-account', 'westus', 'i-123');
    expect(instance).toEqual(
      expect.objectContaining({
        account: 'test-account',
        baseIpAddress: 'i-123.example.com',
        instanceType: 'Standard_D2_v2',
        loadBalancers: ['lb-1'],
        region: 'westus',
        serverGroup: 'fnord-v001',
        vpcId: 'vnet-1',
        vipAddress: ['vip-a', 'vip-b'],
      }),
    );
    expect(instance.healthMetrics[0]).toEqual(
      expect.objectContaining({ extra: 'from details', reason: 'summary reason', state: 'Down' }),
    );
  });

  it('loads details for instances found directly in app.loadBalancers.data', async () => {
    const loadBalancer = {
      account: 'lb-account',
      instances: [{ id: 'i-123', health: [{ type: 'LoadBalancer', state: 'OutOfService' }] }],
      name: 'lb-1',
      region: 'eastus',
      vpcId: 'lb-vnet',
    };

    const instance = await load(app([], [loadBalancer]));

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('lb-account', 'eastus', 'i-123');
    expect(instance).toEqual(
      expect.objectContaining({ account: 'lb-account', loadBalancers: ['lb-1'], region: 'eastus' }),
    );
  });

  it('loads details for instances found in app.loadBalancers.data server groups', async () => {
    const loadBalancer = {
      account: 'lb-account',
      instances: [],
      name: 'lb-server-groups',
      region: 'eastus',
      vpcId: 'lb-vnet',
      serverGroups: [serverGroup({ account: 'other-account', isDisabled: false, region: 'centralus' })],
    };

    const instance = await load(app([], [loadBalancer]));

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('lb-account', 'eastus', 'i-123');
    expect(instance).toEqual(
      expect.objectContaining({ account: 'lb-account', loadBalancers: ['lb-server-groups'], region: 'eastus' }),
    );
  });

  it('loads details for instances in disabled server groups through load balancers', async () => {
    const disabledServerGroup = serverGroup({ account: 'disabled-account', isDisabled: true, region: 'centralus' });
    const loadBalancer = {
      account: 'lb-account',
      instances: [],
      name: 'lb-disabled',
      region: 'eastus',
      vpcId: 'lb-vnet',
      serverGroups: [disabledServerGroup],
    };

    const instance = await load(app([], [loadBalancer]));

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('lb-account', 'eastus', 'i-123');
    expect(instance).toEqual(
      expect.objectContaining({ account: 'lb-account', loadBalancers: ['lb-disabled'], region: 'eastus' }),
    );
  });

  it('loads standalone instance params without an app serverGroups data source', async () => {
    const standaloneApp = { isStandalone: true } as any;
    const instance = await load(
      standaloneApp,
      instanceParams,
      details({ health: [{ type: 'LoadBalancer', state: 'Up' }] }),
    );

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('test-account', 'westus', 'i-123');
    expect(instance).toEqual(expect.objectContaining({ account: 'test-account', region: 'westus' }));
    expect(instance.healthMetrics).toEqual([expect.objectContaining({ type: 'LoadBalancer', state: 'Up' })] as any);
  });

  it('returns not-found state when no summary exists', async () => {
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(undefined);

    const instance = await loadAzureInstanceDetails({ app: app(), instance: instanceParams });

    expect(instance).toEqual({ instanceIdNotFound: 'i-123' } as any);
    expect(InstanceReader.getInstanceDetails).not.toHaveBeenCalled();
  });

  it('renders legacy basic details and not-found content', () => {
    renderRouted(
      <AzureInstanceInformationSection
        instance={
          {
            account: 'test-account',
            instanceType: 'Standard_D2_v2',
            launchTime: 1710000000000,
            provider: 'azure',
            region: 'westus',
          } as any
        }
      />,
    );
    const route = findElement(
      AzureInstanceInformationSection({
        instance: {
          account: 'test-account',
          instanceType: 'Standard_D2_v2',
          launchTime: 1710000000000,
          provider: 'azure',
          region: 'westus',
          serverGroup: 'fnord-v001',
        },
      } as any),
      UISref,
    );

    expect(screen.getByText('Launched')).toBeInTheDocument();
    expect(screen.getByText('test-account')).toBeInTheDocument();
    expect(screen.getByText('westus')).toBeInTheDocument();
    expect(screen.getByText('Standard_D2_v2')).toBeInTheDocument();
    expect(route?.props.to).toBe('^.serverGroup');
    expect(route?.props.params).toEqual({
      accountId: 'test-account',
      provider: 'azure',
      region: 'westus',
      serverGroup: 'fnord-v001',
    });
  });

  it('renders the instance header and not-found state', () => {
    const loaded = renderRouted(
      <AzureInstanceDetails {...routerProps} app={app()} instance={instanceParams} initialInstance={details()} />,
    );
    expect(screen.getByRole('heading', { name: 'i-123' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Instance Information' })).toBeInTheDocument();
    loaded.unmount();
    renderRouted(
      <AzureInstanceDetails
        {...routerProps}
        app={app()}
        instance={instanceParams}
        initialInstance={{ instanceIdNotFound: 'i-missing' } as any}
      />,
    );

    expect(screen.getByRole('heading', { name: 'Instance not found.' })).toBeInTheDocument();
  });

  it('loads the new instance and clears stale details when the mounted instance route changes', async () => {
    const application = app([
      serverGroup({
        instances: [
          { id: 'i-123', health: [], instanceType: 'old-summary-type' },
          { id: 'i-456', health: [], instanceType: 'new-summary-type' },
        ],
      }),
    ]);
    vi.spyOn(
      InstanceReader,
      'getInstanceDetails',
    ).mockImplementation((_account: string, _region: string, instanceId: string) =>
      Promise.resolve(details({ instanceId, instanceType: instanceId === 'i-456' ? 'new-type' : 'old-type' })),
    );
    const rendered = renderRouted(
      <AzureInstanceDetails
        {...routerProps}
        app={application}
        instance={instanceParams}
        initialInstance={details({ instanceId: 'i-123', instanceType: 'old-type' })}
      />,
    );

    rendered.rerenderRouted(
      <AzureInstanceDetails
        {...routerProps}
        app={application}
        instance={{ ...instanceParams, instanceId: 'i-456' }}
        initialInstance={details({ instanceId: 'i-123', instanceType: 'old-type' })}
      />,
    );

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('test-account', 'westus', 'i-456');
    expect(screen.queryByText('old-type')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Instance Information' })).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('new-type')).toBeInTheDocument());
  });

  it('preserves supported instance actions', () => {
    vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    vi.spyOn(InstanceWriter, 'terminateInstance').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'terminateInstanceAndShrinkServerGroup').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'rebootInstance').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'registerInstanceWithLoadBalancer').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'deregisterInstanceFromLoadBalancer').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'enableInstanceInDiscovery').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'disableInstanceInDiscovery').mockReturnValue(Promise.resolve({} as any));
    const application = app();
    const instance = {
      account: 'test-account',
      health: [
        { type: 'LoadBalancer', state: 'OutOfService' },
        { type: 'Discovery', state: 'OutOfService' },
      ],
      instanceId: 'i-123',
      loadBalancers: ['lb-1'],
      serverGroup: 'fnord-v001',
    } as any;
    const rendered = render(<AzureInstanceActions {...routerProps} app={application} instance={instance} />);

    fireEvent.click(screen.getByRole('button', { name: 'Instance Actions' }));
    screen.getAllByRole('menuitem').forEach((item) => fireEvent.click(item));

    expect(ConfirmationModalService.confirm).toHaveBeenCalledTimes(7);
    (ConfirmationModalService.confirm as Mock).mock.calls
      .map((args, __i) => ({
        args,
        returnValue: (ConfirmationModalService.confirm as Mock).mock.results[__i].value,
        invocationOrder: (ConfirmationModalService.confirm as Mock).mock.invocationCallOrder[__i],
      }))
      .forEach((call) => call.args[0].submitMethod());
    expect(InstanceWriter.terminateInstance).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.terminateInstanceAndShrinkServerGroup).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.rebootInstance).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.registerInstanceWithLoadBalancer).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.deregisterInstanceFromLoadBalancer).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.enableInstanceInDiscovery).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.disableInstanceInDiscovery).toHaveBeenCalledWith(instance, application);
    (ConfirmationModalService.confirm as Mock).mock.calls[0][0].taskMonitorConfig.onTaskComplete();
    expect(stateService.includes).toHaveBeenCalledWith('**.instanceDetails', { instanceId: 'i-123' });
    expect(stateService.go).toHaveBeenCalledWith('^');
  });

  it('renders load balancer actions based on load balancer health', () => {
    expect(
      actionLabels({
        health: [{ type: 'LoadBalancer', state: 'OutOfService' }],
        instanceId: 'i-123',
        loadBalancers: ['lb-1'],
      } as any),
    ).toEqual(['Terminate', 'Reboot', 'Register with Load Balancer', 'Deregister from Load Balancer']);

    expect(
      actionLabels({
        health: [{ type: 'LoadBalancer', state: 'InService' }],
        instanceId: 'i-123',
        loadBalancers: ['lb-1'],
      } as any),
    ).toEqual(['Terminate', 'Reboot', 'Deregister from Load Balancer']);

    expect(
      actionLabels({
        health: [],
        instanceId: 'i-123',
        loadBalancers: ['lb-1'],
      } as any),
    ).toEqual(['Terminate', 'Reboot', 'Register with Load Balancer']);
  });

  it('renders discovery actions based on discovery health', () => {
    expect(
      actionLabels({
        health: [{ type: 'Discovery', state: 'OutOfService' }],
        instanceId: 'i-123',
      } as any),
    ).toEqual(['Terminate', 'Reboot', 'Enable in Discovery', 'Disable in Discovery']);

    expect(
      actionLabels({
        health: [{ type: 'Discovery', state: 'Up' }],
        instanceId: 'i-123',
      } as any),
    ).toEqual(['Terminate', 'Reboot', 'Disable in Discovery']);

    expect(actionLabels({ health: [], instanceId: 'i-123' } as any)).toEqual(['Terminate', 'Reboot']);
  });

  it('registers Azure React instance details with the provider registry', () => {
    registerAzureProvider();

    expect(CloudProviderRegistry.getValue('azure', 'instance.details').render).toBe(
      (RoutedAzureInstanceDetails as any).render,
    );
    expect(CloudProviderRegistry.getValue('azure', 'instance.detailsController')).toBeNull();
    expect(CloudProviderRegistry.getValue('azure', 'instance.detailsTemplateUrl')).toBeNull();
  });
});

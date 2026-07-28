import { shallow } from 'enzyme';
import { UISref } from '@uirouter/react';
import React from 'react';
import { MenuItem } from 'react-bootstrap';

import {
  AccountTag,
  CloudProviderRegistry,
  CollapsibleSection,
  ConfirmationModalService,
  InstanceDetailsHeader,
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
  const stateService = { go: jasmine.createSpy('go'), includes: jasmine.createSpy('includes').and.returnValue(true) };
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
        onRefresh: () => jasmine.createSpy('unsubscribe'),
      },
      serverGroups: {
        data: serverGroups,
        ready: () => Promise.resolve(),
        onRefresh: () => jasmine.createSpy('unsubscribe'),
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
    spyOn(InstanceReader, 'getInstanceDetails').and.returnValue(Promise.resolve(fetchedDetails));
    return loadAzureInstanceDetails({ app: appFixture, instance: params });
  }

  function actionLabels(instance: any): string[] {
    return shallow(<AzureInstanceActions {...routerProps} app={app()} instance={instance} />)
      .find(MenuItem)
      .map((item) => String(item.prop('children')).trim());
  }

  it('loads details for instances found in app.serverGroups.data', async () => {
    const instance = await load(app([serverGroup()]));

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('test-account', 'westus', 'i-123');
    expect(instance).toEqual(
      jasmine.objectContaining({
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
      jasmine.objectContaining({ extra: 'from details', reason: 'summary reason', state: 'Down' }),
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
      jasmine.objectContaining({ account: 'lb-account', loadBalancers: ['lb-1'], region: 'eastus' }),
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
      jasmine.objectContaining({ account: 'lb-account', loadBalancers: ['lb-server-groups'], region: 'eastus' }),
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
      jasmine.objectContaining({ account: 'lb-account', loadBalancers: ['lb-disabled'], region: 'eastus' }),
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
    expect(instance).toEqual(jasmine.objectContaining({ account: 'test-account', region: 'westus' }));
    expect(instance.healthMetrics).toEqual([jasmine.objectContaining({ type: 'LoadBalancer', state: 'Up' })] as any);
  });

  it('returns not-found state when no summary exists', async () => {
    spyOn(InstanceReader, 'getInstanceDetails');

    const instance = await loadAzureInstanceDetails({ app: app(), instance: instanceParams });

    expect(instance).toEqual({ instanceIdNotFound: 'i-123' } as any);
    expect(InstanceReader.getInstanceDetails).not.toHaveBeenCalled();
  });

  it('renders legacy basic details and not-found content', () => {
    const wrapper = shallow(
      <AzureInstanceInformationSection
        instance={
          {
            account: 'test-account',
            instanceType: 'Standard_D2_v2',
            launchTime: 1710000000000,
            provider: 'azure',
            region: 'westus',
            serverGroup: 'fnord-v001',
          } as any
        }
      />,
    );
    const content = shallow(<div>{wrapper.find(CollapsibleSection).prop('children')}</div>);
    const text = content.text();

    expect(text).toContain('Launched');
    expect(content.find(AccountTag).prop('account')).toBe('test-account');
    expect(text).toContain('westus');
    expect(text).toContain('Standard_D2_v2');
    expect(content.find(UISref).prop('to')).toBe('^.serverGroup');
    expect(content.find(UISref).prop('params')).toEqual({
      accountId: 'test-account',
      provider: 'azure',
      region: 'westus',
      serverGroup: 'fnord-v001',
    });
  });

  it('renders the instance header and not-found state', () => {
    const loaded = shallow(
      <AzureInstanceDetails {...routerProps} app={app()} instance={instanceParams} initialInstance={details()} />,
    );
    const notFound = shallow(
      <AzureInstanceDetails
        {...routerProps}
        app={app()}
        instance={instanceParams}
        initialInstance={{ instanceIdNotFound: 'i-missing' } as any}
      />,
    );

    expect(loaded.find(InstanceDetailsHeader).prop('instanceId')).toBe('i-123');
    expect(loaded.find(AzureInstanceInformationSection).exists()).toBe(true);
    expect(notFound.text()).toContain('Instance not found.');
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
    spyOn(InstanceReader, 'getInstanceDetails').and.callFake((_account: string, _region: string, instanceId: string) =>
      Promise.resolve(details({ instanceId, instanceType: instanceId === 'i-456' ? 'new-type' : 'old-type' })),
    );
    const wrapper = shallow(
      <AzureInstanceDetails
        {...routerProps}
        app={application}
        instance={instanceParams}
        initialInstance={details({ instanceId: 'i-123', instanceType: 'old-type' })}
      />,
    );

    wrapper.setProps({ instance: { ...instanceParams, instanceId: 'i-456' } });

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('test-account', 'westus', 'i-456');
    expect(wrapper.find(InstanceDetailsHeader).prop('instanceId')).toBe('i-456');
    expect(wrapper.find(AzureInstanceInformationSection).exists()).toBe(false);

    await Promise.resolve();
    await Promise.resolve();
    wrapper.update();

    expect(wrapper.find(AzureInstanceInformationSection).prop('instance').instanceType).toBe('new-type');
  });

  it('preserves supported instance actions', () => {
    spyOn(ConfirmationModalService, 'confirm');
    spyOn(InstanceWriter, 'terminateInstance').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'terminateInstanceAndShrinkServerGroup').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'rebootInstance').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'registerInstanceWithLoadBalancer').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'deregisterInstanceFromLoadBalancer').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'enableInstanceInDiscovery').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'disableInstanceInDiscovery').and.returnValue(Promise.resolve({} as any));
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
    const wrapper = shallow(<AzureInstanceActions {...routerProps} app={application} instance={instance} />);

    wrapper.find(MenuItem).forEach((item) => item.prop('onClick')({} as any));

    expect(ConfirmationModalService.confirm).toHaveBeenCalledTimes(7);
    (ConfirmationModalService.confirm as jasmine.Spy).calls.all().forEach((call) => call.args[0].submitMethod());
    expect(InstanceWriter.terminateInstance).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.terminateInstanceAndShrinkServerGroup).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.rebootInstance).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.registerInstanceWithLoadBalancer).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.deregisterInstanceFromLoadBalancer).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.enableInstanceInDiscovery).toHaveBeenCalledWith(instance, application);
    expect(InstanceWriter.disableInstanceInDiscovery).toHaveBeenCalledWith(instance, application);
    (ConfirmationModalService.confirm as jasmine.Spy).calls.first().args[0].taskMonitorConfig.onTaskComplete();
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

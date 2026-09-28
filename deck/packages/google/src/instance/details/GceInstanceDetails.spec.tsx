import { mount, shallow } from 'enzyme';
import React from 'react';

import type { Action } from '@spinnaker/core';
import {
  AccountService,
  ConfirmationModalService,
  Details,
  InstanceActions,
  InstanceReader,
  InstanceWriter,
  RecentHistoryService,
} from '@spinnaker/core';

import { GceInstanceDetailsComponent as GceInstanceDetails } from './GceInstanceDetails';

describe('GceInstanceDetails', () => {
  function application(loadBalancers: any[] = []) {
    return {
      isStandalone: false,
      name: 'fnord',
      loadBalancers: {
        data: loadBalancers,
        ready: () => Promise.resolve(),
      },
      serverGroups: {
        data: [],
        ready: () => Promise.resolve(),
      },
    } as any;
  }

  function instance(overrides: any = {}) {
    return {
      account: 'test-account',
      cloudProvider: 'gce',
      health: [],
      id: 'instance-1',
      instanceId: 'instance-1',
      loadBalancers: ['network-lb'],
      name: 'instance-1',
      placement: { availabilityZone: 'us-central1-a' },
      provider: 'gce',
      region: 'us-central1',
      serverGroup: 'fnord-v001',
      zone: 'us-central1-a',
      ...overrides,
    } as any;
  }

  function networkLoadBalancer(overrides: any = {}) {
    return {
      account: 'test-account',
      loadBalancerType: 'NETWORK',
      name: 'network-lb',
      ...overrides,
    };
  }

  function actionsFor(
    loadedInstance: any,
    app = application([networkLoadBalancer()]),
    stateService = { go: jasmine.createSpy('go'), includes: () => false },
  ): Action[] {
    const wrapper = shallow(
      <GceInstanceDetails
        app={app}
        initialInstance={loadedInstance}
        router={{} as any}
        stateParams={{}}
        stateService={stateService as any}
      />,
    );
    const renderedActions = shallow(wrapper.find(Details.Header).prop('actions') as React.ReactElement);
    const actionMenu = renderedActions.is(InstanceActions) ? renderedActions : renderedActions.find(InstanceActions);
    const actions = actionMenu.exists() ? actionMenu.prop('actions') : [];
    renderedActions.unmount();
    wrapper.unmount();
    return actions;
  }

  function labelsFor(loadedInstance: any, app?: any): string[] {
    return actionsFor(loadedInstance, app).map(({ label }) => label);
  }

  it('finds instances through disabled server groups attached to load balancers', () => {
    spyOn(RecentHistoryService, 'addExtraDataToLatest');
    spyOn(InstanceReader, 'getInstanceDetails').and.returnValue(
      Promise.resolve(
        instance({
          networkInterfaces: [{ networkIP: '10.0.0.1' }],
          selfLink:
            'https://www.googleapis.com/compute/v1/projects/test-project/zones/us-central1-a/instances/instance-1',
        }),
      ),
    );
    const app = application([
      networkLoadBalancer({
        instances: [],
        region: 'us-central1',
        serverGroups: [
          {
            instances: [{ id: 'instance-1', health: [] }],
            isDisabled: true,
            name: 'fnord-v001',
          },
        ],
      }),
    ]);

    const wrapper = mount(
      <GceInstanceDetails
        app={app}
        instance={{ account: 'route-account', instanceId: 'instance-1', region: 'route-region' }}
        router={{} as any}
        stateParams={{}}
        stateService={{} as any}
      />,
    );

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('test-account', 'us-central1', 'instance-1');
    wrapper.unmount();
  });

  it('clears actions on instance identity changes and ignores stale responses', async () => {
    spyOn(AccountService, 'challengeDestructiveActions').and.returnValue(Promise.resolve(false));
    const oldRequest = deferred<any>();
    const newRequest = deferred<any>();
    spyOn(RecentHistoryService, 'addExtraDataToLatest');
    spyOn(Details, 'Header').and.callFake(({ actions, name }: any) => (
      <div className="test-instance-header">
        {name}
        {actions}
      </div>
    ));
    spyOn(InstanceReader, 'getInstanceDetails').and.callFake(
      (_account: string, _region: string, instanceId: string) => {
        if (instanceId === 'instance-1') {
          return Promise.resolve(instance());
        }
        return instanceId === 'instance-2' ? oldRequest.promise : newRequest.promise;
      },
    );
    const app = application();
    const RoutedDetails = ({ routedInstance }: { routedInstance: any }) => (
      <GceInstanceDetails
        app={app}
        initialInstance={instance()}
        instance={routedInstance}
        router={{} as any}
        stateParams={{}}
        stateService={{} as any}
      />
    );
    const wrapper = mount(
      <RoutedDetails routedInstance={{ account: 'test-account', instanceId: 'instance-1', region: 'us-central1' }} />,
    );
    await settle();
    wrapper.update();
    expect(wrapper.find('.test-instance-header').text()).toContain('instance-1');
    expect(wrapper.find(InstanceActions).exists()).toBe(true);

    wrapper.setProps({
      routedInstance: { account: 'test-account', instanceId: 'instance-2', region: 'us-central1' },
    });
    wrapper.update();
    expect(wrapper.find('.test-instance-header').exists()).toBe(false);
    expect(wrapper.find(InstanceActions).exists()).toBe(false);

    wrapper.setProps({
      routedInstance: { account: 'test-account', instanceId: 'instance-3', region: 'us-central1' },
    });
    wrapper.update();
    expect(wrapper.find('.test-instance-header').exists()).toBe(false);
    expect(wrapper.find(InstanceActions).exists()).toBe(false);

    oldRequest.resolve(instance({ id: 'instance-2', instanceId: 'instance-2', name: 'instance-2' }));
    await settle();
    wrapper.update();
    expect(wrapper.find('.test-instance-header').exists()).toBe(false);
    expect(wrapper.find(InstanceActions).exists()).toBe(false);

    newRequest.resolve(instance({ id: 'instance-3', instanceId: 'instance-3', name: 'instance-3' }));
    await settle();
    wrapper.update();
    expect(wrapper.find('.test-instance-header').text()).toContain('instance-3');
    wrapper.unmount();
  });

  it('shows discovery actions only for their historical health states', () => {
    expect(labelsFor(instance({ health: [{ state: 'OutOfService', type: 'Discovery' }] }))).toEqual([
      'Enable in Discovery',
      'Register with Load Balancer',
      'Reboot',
      'Terminate',
      'Terminate and Shrink Server Group',
    ]);
    expect(labelsFor(instance({ health: [{ state: 'Up', type: 'Discovery' }] }))).toEqual([
      'Disable in Discovery',
      'Register with Load Balancer',
      'Reboot',
      'Terminate',
      'Terminate and Shrink Server Group',
    ]);
    expect(labelsFor(instance({ health: [{ state: 'Down', type: 'Discovery' }] }))).not.toContain(
      'Disable in Discovery',
    );
  });

  it('shows register and deregister actions only for network load balancers in the instance account', () => {
    const outOfService = instance({ health: [{ state: 'OutOfService', type: 'LoadBalancer' }] });
    expect(labelsFor(outOfService)).toContain('Register with Load Balancer');
    expect(labelsFor(outOfService)).toContain('Deregister from Load Balancer');

    const httpApp = application([networkLoadBalancer({ loadBalancerType: 'HTTP' })]);
    expect(labelsFor(outOfService, httpApp)).not.toContain('Register with Load Balancer');
    expect(labelsFor(outOfService, httpApp)).not.toContain('Deregister from Load Balancer');

    const noMatchingLoadBalancerApp = application([]);
    expect(labelsFor(outOfService, noMatchingLoadBalancerApp)).not.toContain('Register with Load Balancer');
    expect(labelsFor(outOfService, noMatchingLoadBalancerApp)).not.toContain('Deregister from Load Balancer');

    const wrongAccountApp = application([networkLoadBalancer({ account: 'other-account' })]);
    expect(labelsFor(outOfService, wrongAccountApp)).not.toContain('Register with Load Balancer');
    expect(labelsFor(outOfService, wrongAccountApp)).not.toContain('Deregister from Load Balancer');

    const accountScopedApp = application([
      networkLoadBalancer(),
      networkLoadBalancer({ account: 'other-account', loadBalancerType: 'HTTP' }),
    ]);
    expect(labelsFor(outOfService, accountScopedApp)).toContain('Register with Load Balancer');
    expect(labelsFor(outOfService, accountScopedApp)).toContain('Deregister from Load Balancer');
  });

  it('passes only eligible network load balancer names to registration writers', () => {
    const confirmation = spyOn(ConfirmationModalService, 'confirm');
    const register = spyOn(InstanceWriter, 'registerInstanceWithLoadBalancer').and.returnValue(
      Promise.resolve({} as any),
    );
    const deregister = spyOn(InstanceWriter, 'deregisterInstanceFromLoadBalancer').and.returnValue(
      Promise.resolve({} as any),
    );
    const app = application([
      networkLoadBalancer(),
      networkLoadBalancer({ loadBalancerType: 'HTTP', name: 'http-lb' }),
      networkLoadBalancer({ account: 'other-account', name: 'other-network-lb' }),
    ]);
    const loadedInstance = instance({
      health: [{ state: 'OutOfService', type: 'LoadBalancer' }],
      loadBalancers: ['network-lb', 'http-lb', 'other-network-lb'],
    });

    const actions = actionsFor(loadedInstance, app);
    actions.find(({ label }) => label === 'Register with Load Balancer')!.triggerAction();
    actions.find(({ label }) => label === 'Deregister from Load Balancer')!.triggerAction();
    const reason = { reason: 'operator requested' };
    confirmation.calls.all().forEach(({ args }) => args[0].submitMethod(reason));

    const eligibleInstance = { ...loadedInstance, loadBalancers: ['network-lb'] };
    expect(register).toHaveBeenCalledWith(eligibleInstance, app, reason);
    expect(deregister).toHaveBeenCalledWith(eligibleInstance, app, reason);
  });

  it('always shows reboot and terminate, and only shows shrink for a managed server group', () => {
    expect(labelsFor(instance({ loadBalancers: [] }))).toEqual([
      'Reboot',
      'Terminate',
      'Terminate and Shrink Server Group',
    ]);
    expect(labelsFor(instance({ loadBalancers: [], serverGroup: undefined }))).toEqual(['Reboot', 'Terminate']);
  });

  it('uses confirmation, account verification, task monitors, and exact GCE writer contracts', () => {
    const confirmation = spyOn(ConfirmationModalService, 'confirm');
    spyOn(InstanceWriter, 'terminateInstance').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'terminateInstanceAndShrinkServerGroup').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'rebootInstance').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'registerInstanceWithLoadBalancer').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'deregisterInstanceFromLoadBalancer').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'enableInstanceInDiscovery').and.returnValue(Promise.resolve({} as any));
    spyOn(InstanceWriter, 'disableInstanceInDiscovery').and.returnValue(Promise.resolve({} as any));
    const $state = { go: jasmine.createSpy('go'), includes: jasmine.createSpy('includes').and.returnValue(true) };
    const app = application([networkLoadBalancer()]);
    const loadedInstance = instance({
      health: [
        { state: 'OutOfService', type: 'Discovery' },
        { state: 'Up', type: 'Discovery' },
        { state: 'OutOfService', type: 'LoadBalancer' },
      ],
    });

    const actions = actionsFor(loadedInstance, app, $state);
    expect(actions.length).toBe(7);
    if (!actions.length) {
      return;
    }
    actions.forEach(({ triggerAction }) => triggerAction());

    expect(confirmation).toHaveBeenCalledTimes(7);
    const confirmations = confirmation.calls.all().map(({ args }) => args[0]);
    confirmations.forEach((params) => {
      expect(params.account).toBe('test-account');
      expect(params.askForReason).toBe(true);
      expect(params.taskMonitorConfig.application).toBe(app);
      expect(params.taskMonitorConfig.title).toBeTruthy();
    });

    const findConfirmation = (header: string) => confirmations.find((params) => params.header === header);
    const reason = { reason: 'operator requested' };
    findConfirmation('Really enable instance-1 in discovery?').submitMethod(reason);
    findConfirmation('Really disable instance-1 in discovery?').submitMethod(reason);
    findConfirmation('Really register instance-1 with network-lb?').submitMethod(reason);
    findConfirmation('Really deregister instance-1 from network-lb?').submitMethod(reason);
    findConfirmation('Really reboot instance-1?').submitMethod(reason);
    findConfirmation('Really terminate instance-1?').submitMethod(reason);
    findConfirmation('Really terminate instance-1 and shrink fnord-v001?').submitMethod(reason);

    expect(InstanceWriter.enableInstanceInDiscovery).toHaveBeenCalledWith(loadedInstance, app, reason);
    expect(InstanceWriter.disableInstanceInDiscovery).toHaveBeenCalledWith(loadedInstance, app, reason);
    expect(InstanceWriter.registerInstanceWithLoadBalancer).toHaveBeenCalledWith(loadedInstance, app, reason);
    expect(InstanceWriter.deregisterInstanceFromLoadBalancer).toHaveBeenCalledWith(loadedInstance, app, reason);
    expect(InstanceWriter.rebootInstance).toHaveBeenCalledWith(loadedInstance, app, {
      interestingHealthProviderNames: [],
      reason: 'operator requested',
    });
    expect(InstanceWriter.terminateInstance).toHaveBeenCalledWith(loadedInstance, app, {
      cloudProvider: 'gce',
      managedInstanceGroupName: 'fnord-v001',
      reason: 'operator requested',
    });
    expect(InstanceWriter.terminateInstanceAndShrinkServerGroup).toHaveBeenCalledWith(loadedInstance, app, {
      instanceIds: ['instance-1'],
      reason: 'operator requested',
      serverGroupName: 'fnord-v001',
      zone: 'us-central1-a',
    });

    findConfirmation('Really terminate instance-1?').taskMonitorConfig.onTaskComplete();
    expect($state.includes).toHaveBeenCalledWith('**.instanceDetails', { instanceId: 'instance-1' });
    expect($state.go).toHaveBeenCalledWith('^');
  });
});

const settle = () => new Promise((resolve) => setTimeout(resolve));

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: any) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

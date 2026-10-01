import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import {
  AccountService,
  ConfirmationModalService,
  Details,
  InstanceReader,
  InstanceWriter,
  RecentHistoryService,
} from '@spinnaker/core';

import { GceInstanceActionsComponent, GceInstanceDetailsComponent as GceInstanceDetails } from './GceInstanceDetails';

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

  function renderActionsFor(
    loadedInstance: any,
    app = application([networkLoadBalancer()]),
    stateService = { go: vi.fn(), includes: () => false },
  ) {
    return render(
      <GceInstanceActionsComponent
        app={app}
        instance={loadedInstance}
        router={{} as any}
        stateParams={{}}
        stateService={stateService as any}
      />,
    );
  }

  function labelsFor(loadedInstance: any, app?: any): string[] {
    const rendered = renderActionsFor(loadedInstance, app);
    const labels = Array.from(rendered.container.querySelectorAll('li a')).map((action) => action.textContent || '');
    rendered.unmount();
    return labels;
  }

  it('finds instances through disabled server groups attached to load balancers', () => {
    vi.spyOn(RecentHistoryService, 'addExtraDataToLatest').mockReturnValue(undefined);
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockReturnValue(
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

    const rendered = render(
      <GceInstanceDetails
        app={app}
        instance={{ account: 'route-account', instanceId: 'instance-1', region: 'route-region' }}
        router={{} as any}
        stateParams={{}}
        stateService={{} as any}
      />,
    );

    expect(InstanceReader.getInstanceDetails).toHaveBeenCalledWith('test-account', 'us-central1', 'instance-1');
    rendered.unmount();
  });

  it('clears actions on instance identity changes and ignores stale responses', async () => {
    vi.spyOn(AccountService, 'challengeDestructiveActions').mockReturnValue(Promise.resolve(false));
    const oldRequest = deferred<any>();
    const newRequest = deferred<any>();
    vi.spyOn(RecentHistoryService, 'addExtraDataToLatest').mockReturnValue(undefined);
    vi.spyOn(Details, 'Header').mockImplementation(({ actions, name }: any) => (
      <div className="test-instance-header">
        {name}
        {actions}
      </div>
    ));
    vi.spyOn(InstanceReader, 'getInstanceDetails').mockImplementation(
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
    const rendered = render(
      <RoutedDetails routedInstance={{ account: 'test-account', instanceId: 'instance-1', region: 'us-central1' }} />,
    );
    await waitFor(() =>
      expect(rendered.container.querySelector('.test-instance-header')).toHaveTextContent('instance-1'),
    );
    expect(within(rendered.container).getByText('Instance Actions')).toBeInTheDocument();

    rendered.rerender(
      <RoutedDetails routedInstance={{ account: 'test-account', instanceId: 'instance-2', region: 'us-central1' }} />,
    );
    expect(rendered.container.querySelector('.test-instance-header')).not.toBeInTheDocument();
    expect(within(rendered.container).queryByText('Instance Actions')).not.toBeInTheDocument();

    rendered.rerender(
      <RoutedDetails routedInstance={{ account: 'test-account', instanceId: 'instance-3', region: 'us-central1' }} />,
    );
    expect(rendered.container.querySelector('.test-instance-header')).not.toBeInTheDocument();
    expect(within(rendered.container).queryByText('Instance Actions')).not.toBeInTheDocument();

    await act(async () =>
      oldRequest.resolve(instance({ id: 'instance-2', instanceId: 'instance-2', name: 'instance-2' })),
    );
    expect(rendered.container.querySelector('.test-instance-header')).not.toBeInTheDocument();
    expect(within(rendered.container).queryByText('Instance Actions')).not.toBeInTheDocument();

    await act(async () =>
      newRequest.resolve(instance({ id: 'instance-3', instanceId: 'instance-3', name: 'instance-3' })),
    );
    await waitFor(() =>
      expect(rendered.container.querySelector('.test-instance-header')).toHaveTextContent('instance-3'),
    );
    rendered.unmount();
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
    const confirmation = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    const register = vi
      .spyOn(InstanceWriter, 'registerInstanceWithLoadBalancer')
      .mockReturnValue(Promise.resolve({} as any));
    const deregister = vi
      .spyOn(InstanceWriter, 'deregisterInstanceFromLoadBalancer')
      .mockReturnValue(Promise.resolve({} as any));
    const app = application([
      networkLoadBalancer(),
      networkLoadBalancer({ loadBalancerType: 'HTTP', name: 'http-lb' }),
      networkLoadBalancer({ account: 'other-account', name: 'other-network-lb' }),
    ]);
    const loadedInstance = instance({
      health: [{ state: 'OutOfService', type: 'LoadBalancer' }],
      loadBalancers: ['network-lb', 'http-lb', 'other-network-lb'],
    });

    const rendered = renderActionsFor(loadedInstance, app);
    fireEvent.click(within(rendered.container).getByText('Register with Load Balancer'));
    fireEvent.click(within(rendered.container).getByText('Deregister from Load Balancer'));
    const reason = { reason: 'operator requested' };
    confirmation.mock.calls
      .map((args, __i) => ({
        args,
        returnValue: confirmation.mock.results[__i].value,
        invocationOrder: confirmation.mock.invocationCallOrder[__i],
      }))
      .forEach(({ args }) => args[0].submitMethod(reason));

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
    const confirmation = vi.spyOn(ConfirmationModalService, 'confirm').mockReturnValue(undefined);
    vi.spyOn(InstanceWriter, 'terminateInstance').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'terminateInstanceAndShrinkServerGroup').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'rebootInstance').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'registerInstanceWithLoadBalancer').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'deregisterInstanceFromLoadBalancer').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'enableInstanceInDiscovery').mockReturnValue(Promise.resolve({} as any));
    vi.spyOn(InstanceWriter, 'disableInstanceInDiscovery').mockReturnValue(Promise.resolve({} as any));
    const $state = { go: vi.fn(), includes: vi.fn().mockReturnValue(true) };
    const app = application([networkLoadBalancer()]);
    const loadedInstance = instance({
      health: [
        { state: 'OutOfService', type: 'Discovery' },
        { state: 'Up', type: 'Discovery' },
        { state: 'OutOfService', type: 'LoadBalancer' },
      ],
    });

    const rendered = renderActionsFor(loadedInstance, app, $state);
    const actions = Array.from(rendered.container.querySelectorAll('li a'));
    expect(actions).toHaveLength(7);
    actions.forEach((action) => fireEvent.click(action));

    expect(confirmation).toHaveBeenCalledTimes(7);
    const confirmations = confirmation.mock.calls
      .map((args, __i) => ({
        args,
        returnValue: confirmation.mock.results[__i].value,
        invocationOrder: confirmation.mock.invocationCallOrder[__i],
      }))
      .map(({ args }) => args[0]);
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

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: any) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

import type { ShallowWrapper } from 'enzyme';
import { shallow } from 'enzyme';
import React from 'react';

import { AccountService, RequestBuilder } from '@spinnaker/core';

import type { IEcsCapacityProviderStrategyItem, IEcsServerGroupCommand } from '../serverGroupConfiguration.service';
import { EcsCloneServerGroupModal } from './EcsCloneServerGroupModal';

const buildCommand = (overrides: Partial<IEcsServerGroupCommand> = {}): IEcsServerGroupCommand =>
  ({
    backingData: {
      filtered: {
        availableCapacityProviders: [],
        defaultCapacityProviderStrategy: [],
        ecsClusters: [],
      },
      launchTypes: ['EC2', 'FARGATE'],
    },
    capacityProviderStrategy: [],
    containerMappings: [],
    targetGroupMappings: [],
    taskDefinitionArtifact: {},
    useDefaultCapacityProviders: true,
    viewState: { contextImages: [], dirty: {} },
    ...overrides,
  } as any);

const buildProps = (command: IEcsServerGroupCommand) =>
  ({
    application: { name: 'app' },
    closeModal: jasmine.createSpy('closeModal'),
    command,
    dismissModal: jasmine.createSpy('dismissModal'),
    title: 'Deploy ECS server group',
  } as any);

const buildUnrenderedModal = (command = buildCommand()): EcsCloneServerGroupModal => {
  const modal = new EcsCloneServerGroupModal(buildProps(command));
  spyOn(modal, 'setState').and.callFake((state: any, callback?: () => void) => {
    modal.state = { ...modal.state, ...state };
    callback?.();
  });
  return modal;
};

const findByTestId = (wrapper: ShallowWrapper, testId: string): ShallowWrapper =>
  wrapper.findWhere((node) => node.prop('data-test-id') === testId);

describe('EcsCloneServerGroupModal', () => {
  it('does not set state when backing data resolves after unmount', async () => {
    const modal = new EcsCloneServerGroupModal(buildProps(buildCommand())) as any;
    let resolveBackingData: () => void;
    spyOn(modal, 'loadBackingData').and.returnValue(
      new Promise<void>((resolve) => {
        resolveBackingData = resolve;
      }),
    );
    const setState = spyOn(modal, 'setState');

    const configurePromise = modal.configureCommand();
    modal.componentWillUnmount();
    resolveBackingData();
    await configurePromise;

    expect(setState).not.toHaveBeenCalled();
  });

  it('ignores stale backing data requests', async () => {
    const firstCommand = buildCommand({ ecsClusterName: 'first-cluster' });
    const secondCommand = buildCommand({ ecsClusterName: 'second-cluster' });
    const modal = new EcsCloneServerGroupModal(buildProps(firstCommand)) as any;
    let resolveFirst: () => void;
    let resolveSecond: () => void;
    spyOn(modal, 'loadBackingData').and.callFake((command: IEcsServerGroupCommand) => {
      return new Promise<void>((resolve) => {
        if (command === firstCommand) {
          resolveFirst = resolve;
        } else {
          resolveSecond = resolve;
        }
      });
    });
    spyOn(modal, 'setState').and.callFake((state: any) => {
      modal.state = { ...modal.state, ...state };
    });

    const firstConfigure = modal.configureCommand();
    modal.state = { ...modal.state, command: secondCommand };
    const secondConfigure = modal.configureCommand();

    resolveSecond();
    await secondConfigure;
    expect(modal.state.command).toBe(secondCommand);

    resolveFirst();
    await firstConfigure;
    expect(modal.state.command).toBe(secondCommand);
  });

  it('does not apply backing data from stale requests', async () => {
    const originalHttpClient = RequestBuilder.defaultHttpClient;
    const command = buildCommand({ backingData: { filtered: {} } as any });
    const modal = new EcsCloneServerGroupModal(buildProps(command)) as any;
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(Promise.resolve({}));
    RequestBuilder.defaultHttpClient = {
      get: (config: any) => {
        if (config.url.endsWith('loadBalancers')) {
          return Promise.resolve([{ name: 'stale-load-balancer' }]);
        }
        if (config.url.endsWith('subnets/ecs')) {
          return Promise.resolve([]);
        }
        if (config.url.endsWith('securityGroups')) {
          return Promise.resolve({});
        }
        return Promise.resolve([]);
      },
    } as any;
    spyOn(modal.iamRoleReader, 'listRoles').and.returnValue(Promise.resolve([]));
    spyOn(modal.ecsClusterReader, 'listClusters').and.returnValue(Promise.resolve([]));
    spyOn(modal.ecsClusterReader, 'describeClusters').and.returnValue(Promise.resolve([]));
    spyOn(modal.metricAlarmReader, 'listMetricAlarms').and.returnValue(Promise.resolve([]));
    spyOn(modal.secretReader, 'listSecrets').and.returnValue(Promise.resolve([]));

    modal.configureRequest = 2;
    await modal.loadBackingData(command, '', 1);
    RequestBuilder.defaultHttpClient = originalHttpClient;

    expect(command.backingData.loadBalancers).toBeUndefined();
  });

  it('initializes optional backing data arrays used by child sections', () => {
    const command = buildCommand({ backingData: { filtered: {} } as any });
    const modal = buildUnrenderedModal(command) as any;

    modal.ensureCommandShape(command);

    expect(command.backingData.filtered.iamRoles).toEqual([]);
    expect(command.backingData.filtered.metricAlarms).toEqual([]);
    expect(command.backingData.filtered.secrets).toEqual([]);
    expect(command.backingData.filtered.serviceDiscoveryRegistries).toEqual([]);
  });

  it('filters security groups when the subnet type changes', () => {
    const command = buildCommand({
      backingData: {
        ...buildCommand().backingData,
        securityGroups: {
          'ecs-account': {
            ecs: {
              'us-west-2': [
                { name: 'private-sg', vpcId: 'vpc-1' },
                { name: 'public-sg', vpcId: 'vpc-2' },
              ],
            },
          },
        },
        subnets: [
          { account: 'ecs-account', purpose: 'private-subnet', region: 'us-west-2', vpcId: 'vpc-1' },
          { account: 'ecs-account', purpose: 'public-subnet', region: 'us-west-2', vpcId: 'vpc-2' },
        ],
      } as any,
      credentials: 'ecs-account',
      region: 'us-west-2',
      subnetTypes: ['private-subnet'],
    });
    const modal = buildUnrenderedModal(command) as any;

    modal.attachEventHandlers(command);
    command.subnetTypeChanged(command);

    expect(command.backingData.filtered.securityGroupNames).toEqual(['private-sg']);
  });

  it('reloads backing data when account or region changes', () => {
    const command = buildCommand();
    const modal = buildUnrenderedModal(command) as any;
    const configureCommand = spyOn(modal, 'configureCommand').and.returnValue(Promise.resolve());

    modal.attachEventHandlers(command);
    command.credentialsChanged(command);
    command.regionChanged(command);

    expect(configureCommand).toHaveBeenCalledTimes(2);
  });

  it('notifies child updates with a fresh command state object', () => {
    const command = buildCommand();
    const modal = new EcsCloneServerGroupModal(buildProps(command)) as any;
    const setState = spyOn(modal, 'setState');

    modal.notifyAngular('launchType', 'FARGATE');

    const nextState = setState.calls.mostRecent().args[0] as any;
    expect(nextState.command).not.toBe(command);
    expect(nextState.command.launchType).toBe('FARGATE');
  });

  it('keeps capacity provider mode isolated per modal instance', () => {
    const firstModal = buildUnrenderedModal() as any;
    const secondModal = buildUnrenderedModal() as any;

    firstModal.useCustomCapacityProviders();

    expect(firstModal.lastCapacityProviderMode).toBe('custom');
    expect(secondModal.lastCapacityProviderMode).toBeNull();
  });

  it('starts custom capacity provider mode empty when no custom strategy exists', () => {
    const command = buildCommand();
    const modal = buildUnrenderedModal(command) as any;

    modal.useCustomCapacityProviders();
    modal.addCapacityProvider();

    expect(command.capacityProviderStrategy).toEqual([{ base: null, capacityProvider: '', weight: null } as any]);
  });

  it('does not preserve default capacity provider strategy when switching to custom mode', () => {
    const command = buildCommand({
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE_SPOT', weight: 1 }],
      useDefaultCapacityProviders: true,
    });
    const modal = buildUnrenderedModal(command) as any;

    modal.useCustomCapacityProviders();
    modal.addCapacityProvider();

    expect(command.capacityProviderStrategy).toEqual([{ base: null, capacityProvider: '', weight: null } as any]);
  });

  it('clears existing strategy when custom capacity provider mode is selected', () => {
    const command = buildCommand({
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE_SPOT', weight: 1 }],
      useDefaultCapacityProviders: false,
    });
    const modal = buildUnrenderedModal(command) as any;

    modal.useCustomCapacityProviders();
    modal.addCapacityProvider();

    expect(command.capacityProviderStrategy).toEqual([{ base: null, capacityProvider: '', weight: null } as any]);
  });

  it('appends new capacity providers without replacing existing strategy entries', () => {
    const existingStrategy: IEcsCapacityProviderStrategyItem = { base: 0, capacityProvider: 'FARGATE', weight: 1 };
    const command = buildCommand({
      capacityProviderStrategy: [existingStrategy],
      useDefaultCapacityProviders: false,
    });
    const modal = buildUnrenderedModal(command) as any;

    modal.addCapacityProvider();

    expect(command.capacityProviderStrategy).toEqual([
      existingStrategy,
      { base: null, capacityProvider: '', weight: null } as any,
    ]);
  });

  it('renders and updates each custom capacity provider row by index', () => {
    const command = buildCommand({
      capacityProviderMode: 'custom',
      capacityProviderStrategy: [
        { base: 0, capacityProvider: 'FARGATE', weight: 1 },
        { base: 1, capacityProvider: 'FARGATE_SPOT', weight: 2 },
      ],
      useDefaultCapacityProviders: false,
    });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);

    wrapper.setState({ loaded: true });
    expect(findByTestId(wrapper, 'ServerGroup.capacityProvider.base.1').prop('value')).toBe(1);

    findByTestId(wrapper, 'ServerGroup.capacityProvider.weight.1').simulate('change', { target: { value: '3' } });

    expect(command.capacityProviderStrategy[1].weight).toBe(3);
  });

  it('renders custom capacity provider names as standard inputs', () => {
    const command = buildCommand({
      capacityProviderMode: 'custom',
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }],
      useDefaultCapacityProviders: false,
    });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);

    wrapper.setState({ loaded: true });
    const nameInput = findByTestId(wrapper, 'ServerGroup.customCapacityProvider.name.0');

    expect(nameInput.type()).toBe('input');
    expect(nameInput.prop('value')).toBe('FARGATE');

    nameInput.simulate('change', { target: { value: 'FARGATE_SPOT' } });

    expect(command.capacityProviderStrategy[0].capacityProvider).toBe('FARGATE_SPOT');
  });

  it('does not show custom capacity provider controls for matching default strategy values', () => {
    const command = buildCommand({
      capacityProviderMode: 'default',
      capacityProviderStrategy: [{ base: 1, capacityProvider: 'FARGATE_SPOT', weight: 2 }],
      useDefaultCapacityProviders: true,
    });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);

    wrapper.setState({ loaded: true });

    expect(findByTestId(wrapper, 'ServerGroup.addCapacityProvider').exists()).toBe(false);
    expect(findByTestId(wrapper, 'ServerGroup.defaultCapacityProvider.name.0').prop('value')).toBe('FARGATE_SPOT');
  });

  it('does not synthesize FARGATE_SPOT when the cluster has no default capacity provider strategy', () => {
    const command = buildCommand({
      backingData: { ...buildCommand().backingData, filtered: { defaultCapacityProviderStrategy: [] } } as any,
      capacityProviderStrategy: [],
      useDefaultCapacityProviders: false,
    });
    const modal = buildUnrenderedModal(command) as any;

    modal.useDefaultCapacityProviders();

    expect(command.capacityProviderStrategy).toEqual([]);
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);
    wrapper.setState({ loaded: true });
    expect(findByTestId(wrapper, 'ServerGroup.defaultCapacityProvider.name.0').prop('value')).toBe('');
  });

  it('resets capacity provider mode when a template is selected', () => {
    const command = buildCommand();
    const modal = buildUnrenderedModal(command) as any;
    spyOn(modal, 'configureCommand').and.returnValue(Promise.resolve());
    modal.lastCapacityProviderMode = 'custom';

    modal.templateSelected();

    expect(modal.lastCapacityProviderMode).toBeNull();
  });

  it('renders a capacity provider option only for the active custom row', () => {
    const command = buildCommand({
      capacityProviderMode: 'custom',
      capacityProviderStrategy: [
        { base: 0, capacityProvider: 'FARGATE', weight: 1 },
        { base: 1, capacityProvider: 'FARGATE_SPOT', weight: 2 },
      ],
      useDefaultCapacityProviders: false,
    });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);

    wrapper.setState({ activeCapacityProviderIndex: null, loaded: true });
    expect(wrapper.find('.Select-option').length).toBe(0);

    wrapper.setState({ activeCapacityProviderIndex: 1 });
    expect(wrapper.find('.Select-option').length).toBe(1);
  });
});

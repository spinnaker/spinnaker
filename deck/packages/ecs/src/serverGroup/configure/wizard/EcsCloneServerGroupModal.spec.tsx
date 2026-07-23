import type { ShallowWrapper } from 'enzyme';
import { mount, shallow } from 'enzyme';
import React from 'react';

import {
  AccountService,
  AccountSelectInput,
  DeploymentStrategySelector,
  RegionSelectInput,
  RequestBuilder,
  TaskMonitor,
  TetheredSelect,
  MapEditor,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';

import type { IEcsServerGroupCommand } from '../serverGroupConfiguration.service';
import { ServiceDiscoveryReader } from '../../../serviceDiscovery/serviceDiscovery.read.service';
import { EcsCloneServerGroupModalComponent as EcsCloneServerGroupModal } from './EcsCloneServerGroupModal';
import { BasicSettings } from './pages/BasicSettings';
import { NetworkingSettings } from './pages/NetworkingSettings';
import { EcsNetworking } from './networking/Networking';
import { TaskDefinitionSettings } from './pages/TaskDefinitionSettings';
import { TaskDefinition } from './taskDefinition/TaskDefinition';
import { ContainerSettings } from './pages/ContainerSettings';
import { Container } from './container/Container';
import { HorizontalScalingSettings } from './pages/HorizontalScalingSettings';
import { EcsCapacityProvider } from './capacityProvider/CapacityProvider';
import { LoggingSettings } from './pages/LoggingSettings';
import { ServiceDiscoverySettings } from './pages/ServiceDiscoverySettings';
import { ServiceDiscovery } from './serviceDiscovery/ServiceDiscovery';
import { AdvancedSettings } from './pages/AdvancedSettings';

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
    application: {
      name: 'app',
      serverGroups: {
        onNextRefresh: jasmine.createSpy('onNextRefresh'),
        refresh: jasmine.createSpy('refresh'),
      },
    },
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

const renderCapacityProvider = (command: IEcsServerGroupCommand): ShallowWrapper => {
  const wrapper = shallow(
    <EcsCapacityProvider
      command={command}
      configureCommand={() => Promise.resolve()}
      notifyAngular={(field, value) => (command[field] = value)}
    />,
    { disableLifecycleMethods: true } as any,
  );
  wrapper.setState({ capacityProviderLoadedFlag: true });
  return wrapper;
};

describe('EcsCloneServerGroupModal', () => {
  beforeEach(() => {
    spyOn(TaskMonitor, 'modalInstanceEmulation').and.returnValue({
      dismiss: jasmine.createSpy('dismiss'),
      result: Promise.resolve(),
    } as any);
  });

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

  it('does not replace edits made while configureCommand is pending', async () => {
    const command = buildCommand({ capacity: { desired: 1, max: 2, min: 0 } });
    const modal = buildUnrenderedModal(command) as any;
    let resolveBackingData: () => void;
    spyOn(modal, 'loadBackingData').and.returnValue(
      new Promise<void>((resolve) => {
        resolveBackingData = resolve;
      }),
    );
    const formik = {
      setFieldValue: jasmine.createSpy('setFieldValue'),
      setValues: jasmine.createSpy('setValues'),
      values: command,
    } as any;

    const configurePromise = modal.configureCommand();
    modal.updateCommand(formik, 'capacity', { desired: 3, max: 4, min: 0 });
    resolveBackingData();
    await configurePromise;

    expect(modal.state.command.capacity).toEqual({ desired: 3, max: 4, min: 0 });
  });

  it('merges refreshed backing data into edits made while configureCommand is pending', async () => {
    const command = buildCommand({
      backingData: {
        filtered: {
          availableCapacityProviders: ['old-provider'],
          defaultCapacityProviderStrategy: [],
          ecsClusters: [],
          images: [],
          targetGroups: ['old-target'],
        },
      } as any,
      capacity: { desired: 1, max: 2, min: 0 },
      stack: 'old-stack',
    });
    const modal = buildUnrenderedModal(command) as any;
    modal.attachEventHandlers(command);
    let resolveBackingData: () => void;
    spyOn(modal, 'loadBackingData').and.callFake(
      (requestCommand: IEcsServerGroupCommand) =>
        new Promise<void>((resolve) => {
          resolveBackingData = () => {
            const images = [{ imageId: 'registry/refreshed:latest' }];
            requestCommand.backingData = {
              ...requestCommand.backingData,
              filtered: {
                ...requestCommand.backingData.filtered,
                availableCapacityProviders: ['refreshed-provider'],
                images,
                targetGroups: ['refreshed-target'],
              },
              images,
            } as any;
            resolve();
          };
        }),
    );
    const formik = {
      setFieldValue: jasmine.createSpy('setFieldValue'),
      setValues: jasmine.createSpy('setValues'),
      values: command,
    } as any;

    const configurePromise = modal.configureCommand('refreshed');
    modal.updateCommand(formik, 'stack', 'edited-stack');
    modal.updateCommand(formik, 'capacity', { desired: 3, max: 4, min: 0 });
    resolveBackingData();
    await configurePromise;

    expect(modal.state.command.stack).toBe('edited-stack');
    expect(modal.state.command.capacity).toEqual({ desired: 3, max: 4, min: 0 });
    expect(modal.state.command.backingData.filtered.targetGroups).toEqual(['refreshed-target']);
    expect(modal.state.command.backingData.filtered.images).toEqual([{ imageId: 'registry/refreshed:latest' }]);
    expect(modal.state.command.backingData.filtered.availableCapacityProviders).toEqual(['refreshed-provider']);
  });

  it('ignores an in-flight response and refreshes options when subnet types change', async () => {
    const command = buildCommand({
      backingData: {
        filtered: {
          availableCapacityProviders: [],
          defaultCapacityProviderStrategy: [],
          ecsClusters: [],
          securityGroupNames: ['initial-security-group'],
          subnetTypes: [{ purpose: 'old-subnet', vpcId: 'old-vpc' }],
        },
      } as any,
      subnetTypes: ['old-subnet'],
    });
    const modal = buildUnrenderedModal(command) as any;
    modal.attachEventHandlers(command);
    const requests: Array<{
      command: IEcsServerGroupCommand;
      promise: Promise<void>;
      resolve: () => void;
    }> = [];
    const loadBackingData = spyOn(modal, 'loadBackingData').and.callFake((requestCommand: IEcsServerGroupCommand) => {
      let resolve: () => void;
      const promise = new Promise<void>((requestResolve) => {
        resolve = requestResolve;
      });
      requests.push({ command: requestCommand, promise, resolve });
      return promise;
    });
    const formik = {
      setFieldValue: jasmine.createSpy('setFieldValue'),
      setValues: jasmine.createSpy('setValues'),
      values: command,
    } as any;

    const oldConfigure = modal.configureCommand();
    modal.updateCommand(formik, 'subnetTypes', ['new-subnet']);

    expect(loadBackingData).toHaveBeenCalledTimes(2);
    const newRequest = requests[1];
    if (newRequest) {
      newRequest.command.backingData = {
        ...newRequest.command.backingData,
        filtered: {
          ...newRequest.command.backingData.filtered,
          securityGroupNames: ['new-security-group'],
          subnetTypes: [{ purpose: 'new-subnet', vpcId: 'new-vpc' }],
        },
      } as any;
      newRequest.resolve();
      await newRequest.promise;
    }

    const oldRequest = requests[0];
    oldRequest.command.backingData = {
      ...oldRequest.command.backingData,
      filtered: {
        ...oldRequest.command.backingData.filtered,
        securityGroupNames: ['old-security-group'],
        subnetTypes: [{ purpose: 'old-subnet', vpcId: 'old-vpc' }],
      },
    } as any;
    oldRequest.resolve();
    await oldConfigure;

    expect(modal.state.command.subnetTypes).toEqual(['new-subnet']);
    expect(modal.state.command.backingData.filtered.securityGroupNames).toEqual(['new-security-group']);
    expect(modal.state.command.backingData.filtered.subnetTypes).toEqual([{ purpose: 'new-subnet', vpcId: 'new-vpc' }]);
  });

  it('preserves image options while refreshing security groups after a subnet change', async () => {
    const originalHttpClient = RequestBuilder.defaultHttpClient;
    const images = [
      { id: 'backing-api-v1', imageId: 'registry/api:v1' },
      { id: 'backing-api-v2', imageId: 'registry/api:v2' },
    ];
    const subnets = [
      { account: 'ecs-account', purpose: 'old-subnet', region: 'us-west-2', vpcId: 'old-vpc' },
      { account: 'ecs-account', purpose: 'new-subnet', region: 'us-west-2', vpcId: 'new-vpc' },
    ];
    const securityGroups = {
      'ecs-account': {
        ecs: {
          'us-west-2': [
            { name: 'old-security-group', vpcId: 'old-vpc' },
            { name: 'new-security-group', vpcId: 'new-vpc' },
          ],
        },
      },
    };
    const command = buildCommand({
      backingData: {
        filtered: {
          availableCapacityProviders: [],
          defaultCapacityProviderStrategy: [],
          ecsClusters: [],
          images,
          securityGroupNames: ['old-security-group'],
        },
        images,
      } as any,
      containerMappings: [
        {
          containerName: 'worker',
          imageDescription: { id: 'mapped-api-v2', imageId: 'registry/api:v2' },
        } as any,
      ],
      credentials: 'ecs-account',
      imageDescription: { id: 'selected-api-v1', imageId: 'registry/api:v1' } as any,
      region: 'us-west-2',
      subnetTypes: ['old-subnet'],
    });
    const modal = buildUnrenderedModal(command) as any;
    modal.attachEventHandlers(command);
    const securityGroupRequests: Array<{
      promise: Promise<any>;
      resolve: (value: any) => void;
    }> = [];
    RequestBuilder.defaultHttpClient = {
      get: (config: any) => {
        if (config.url.endsWith('securityGroups')) {
          let resolve: (value: any) => void;
          const promise = new Promise<any>((requestResolve) => {
            resolve = requestResolve;
          });
          securityGroupRequests.push({ promise, resolve });
          return promise;
        }
        if (config.url.endsWith('subnets/ecs')) {
          return Promise.resolve(subnets);
        }
        return Promise.resolve([]);
      },
    } as any;
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({
        'ecs-account': { regions: [{ availabilityZones: ['us-west-2a'], name: 'us-west-2' }] },
      }) as any,
    );
    spyOn(modal.iamRoleReader, 'listRoles').and.returnValue(Promise.resolve([]));
    spyOn(modal.ecsClusterReader, 'listClusters').and.returnValue(Promise.resolve([]));
    spyOn(modal.ecsClusterReader, 'describeClusters').and.returnValue(Promise.resolve([]));
    spyOn(modal.metricAlarmReader, 'listMetricAlarms').and.returnValue(Promise.resolve([]));
    spyOn(modal.secretReader, 'listSecrets').and.returnValue(Promise.resolve([]));
    spyOn(ServiceDiscoveryReader, 'listServiceDiscoveryRegistries').and.returnValue(Promise.resolve([]));
    const configureCommand = spyOn(modal, 'configureCommand').and.callThrough();
    const formik = {
      setFieldValue: jasmine.createSpy('setFieldValue'),
      setValues: jasmine.createSpy('setValues'),
      values: command,
    } as any;

    try {
      const oldConfigure = modal.configureCommand();
      modal.updateCommand(formik, 'subnetTypes', ['new-subnet']);
      const newConfigure = configureCommand.calls.mostRecent().returnValue;

      securityGroupRequests[1].resolve(securityGroups);
      await newConfigure;
      securityGroupRequests[0].resolve(securityGroups);
      await oldConfigure;

      expect(modal.state.command.backingData.filtered.securityGroupNames).toEqual(['new-security-group']);
      expect(modal.state.command.backingData.filtered.images.map((image: any) => image.imageId)).toEqual([
        'registry/api:v1',
        'registry/api:v2',
      ]);

      modal.updateCommand(formik, 'subnetTypes', ['old-subnet']);
      const repeatedConfigure = configureCommand.calls.mostRecent().returnValue;
      securityGroupRequests[2].resolve(securityGroups);
      await repeatedConfigure;

      expect(modal.state.command.backingData.filtered.securityGroupNames).toEqual(['old-security-group']);
      expect(modal.state.command.backingData.filtered.images.map((image: any) => image.imageId)).toEqual([
        'registry/api:v1',
        'registry/api:v2',
      ]);
    } finally {
      RequestBuilder.defaultHttpClient = originalHttpClient;
    }
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

  it('populates regions and availability zones for the selected account during initial configuration', async () => {
    const originalHttpClient = RequestBuilder.defaultHttpClient;
    const regions = [
      { availabilityZones: ['eu-west-1a', 'eu-west-1b'], name: 'eu-west-1' },
      { availabilityZones: ['us-east-1a'], name: 'us-east-1' },
    ];
    const command = buildCommand({
      backingData: { filtered: {} } as any,
      credentials: 'ecs-account',
      region: 'eu-west-1',
    });
    const modal = new EcsCloneServerGroupModal(buildProps(command)) as any;
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(
      Promise.resolve({ 'ecs-account': { regions } }) as any,
    );
    RequestBuilder.defaultHttpClient = {
      get: (config: any) => Promise.resolve(config.url.endsWith('securityGroups') ? {} : []),
    } as any;
    spyOn(modal.iamRoleReader, 'listRoles').and.returnValue(Promise.resolve([]));
    spyOn(modal.ecsClusterReader, 'listClusters').and.returnValue(Promise.resolve([]));
    spyOn(modal.ecsClusterReader, 'describeClusters').and.returnValue(Promise.resolve([]));
    spyOn(modal.metricAlarmReader, 'listMetricAlarms').and.returnValue(Promise.resolve([]));
    spyOn(modal.secretReader, 'listSecrets').and.returnValue(Promise.resolve([]));
    spyOn(ServiceDiscoveryReader, 'listServiceDiscoveryRegistries').and.returnValue(Promise.resolve([]));

    await modal.loadBackingData(command, '');
    RequestBuilder.defaultHttpClient = originalHttpClient;

    expect(command.backingData.filtered.regions).toEqual(regions);
    expect(command.backingData.filtered.availabilityZones).toEqual(['eu-west-1a', 'eu-west-1b']);
    expect(command.availabilityZones).toEqual(['eu-west-1a', 'eu-west-1b']);
  });

  it('reconciles invalid location selections before account and region handlers reload backing data', () => {
    const command = buildCommand({
      backingData: {
        credentialsKeyedByAccount: {
          'account-b': { regions: [{ availabilityZones: ['us-east-1a'], name: 'us-east-1' }] },
        },
        filtered: { regions: [{ availabilityZones: ['eu-west-1a'], name: 'eu-west-1' }] },
      } as any,
      credentials: 'account-b',
      region: 'eu-west-1',
    });
    const modal = buildUnrenderedModal(command) as any;
    const configureCommand = spyOn(modal, 'configureCommand').and.returnValue(Promise.resolve());

    modal.attachEventHandlers(command);
    command.credentialsChanged(command);

    expect(command.backingData.filtered.regions).toEqual([{ availabilityZones: ['us-east-1a'], name: 'us-east-1' }]);
    expect(command.region).toBeNull();
    expect(configureCommand).toHaveBeenCalledTimes(1);

    command.region = 'not-an-account-region';
    command.regionChanged(command);

    expect(command.region).toBeNull();
    expect(configureCommand).toHaveBeenCalledTimes(2);
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

  ['createPipeline', 'editPipeline'].forEach((mode) => {
    it(`returns the transformed command without executing infrastructure in ${mode} mode`, () => {
      const command = buildCommand({ viewState: { contextImages: [], dirty: {}, mode } as any });
      const modal = buildUnrenderedModal(command) as any;
      const cloneServerGroup = jasmine.createSpy('cloneServerGroup');

      modal.submit();

      expect(modal.props.closeModal).toHaveBeenCalledWith(command);
      expect(cloneServerGroup).not.toHaveBeenCalled();
    });
  });

  it('uses authoritative Formik values for account normalization, display, and submission', () => {
    const regions = [{ availabilityZones: ['us-east-1a'], name: 'us-east-1' }];
    const command = buildCommand({
      backingData: {
        credentialsKeyedByAccount: {
          'account-a': { regions: [{ availabilityZones: ['eu-west-1a'], name: 'eu-west-1' }] },
          'account-b': { regions },
        },
        filtered: { regions: [{ availabilityZones: ['eu-west-1a'], name: 'eu-west-1' }] },
      } as any,
      credentials: 'account-a',
      region: 'eu-west-1',
      viewState: { contextImages: [], dirty: {}, mode: 'editPipeline' } as any,
    });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);
    const modal = wrapper.instance() as any;
    spyOn(modal, 'configureCommand').and.returnValue(Promise.resolve());
    modal.attachEventHandlers(command);
    const formik: any = {
      setFieldValue: jasmine.createSpy('setFieldValue').and.callFake((field: string, value: any) => {
        formik.values = { ...formik.values, [field]: value };
      }),
      setValues: jasmine.createSpy('setValues').and.callFake((values: IEcsServerGroupCommand) => {
        formik.values = values;
      }),
      values: { ...command },
    };
    const wizardModal = wrapper.find(WizardModal);
    const renderPages = () =>
      shallow(
        <div>
          {wizardModal.prop('render')({
            formik,
            nextIdx: (() => {
              let index = 0;
              return () => ++index;
            })(),
            wizard: {} as any,
          })}
        </div>,
      );

    const basicPage = renderPages()
      .find(WizardPage)
      .filterWhere((page) => page.prop('label') === 'Basic Settings');
    const basicSettings = basicPage.prop('render')({
      innerRef: React.createRef(),
      onLoadingChanged: () => undefined,
    }).props.children;
    basicSettings.props.onFieldChange('credentials', 'account-b');

    const displayedCommand = renderPages()
      .find(WizardPage)
      .filterWhere((page) => page.prop('label') === 'Basic Settings')
      .prop('render')({ innerRef: React.createRef(), onLoadingChanged: () => undefined }).props.children.props.command;
    expect(formik.setValues).toHaveBeenCalled();
    expect(displayedCommand.credentials).toBe('account-b');
    expect(displayedCommand.region).toBeNull();
    expect(displayedCommand.backingData.filtered.regions).toEqual(regions);

    wizardModal.prop('closeModal')(formik.values);
    expect(wrapper.instance().props.closeModal).toHaveBeenCalledWith(displayedCommand);
  });

  it('uses authoritative Formik values across artifact and input mode normalization and submission', () => {
    const command = buildCommand({
      serviceDiscoveryAssociations: [
        {
          containerName: 'api',
          containerPort: 8080,
          registry: { displayName: 'registry', id: 'registry' },
        } as any,
      ],
      useTaskDefinitionArtifact: true,
      viewState: { contextImages: [], dirty: {}, mode: 'editPipeline' } as any,
    });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);
    const formik: any = {
      setFieldValue: jasmine.createSpy('setFieldValue').and.callFake((field: string, value: any) => {
        formik.values = { ...formik.values, [field]: value };
      }),
      setValues: jasmine.createSpy('setValues').and.callFake((values: IEcsServerGroupCommand) => {
        formik.values = values;
      }),
      values: { ...command },
    };
    const wizardModal = wrapper.find(WizardModal);
    const renderPages = () =>
      shallow(
        <div>
          {wizardModal.prop('render')({
            formik,
            nextIdx: (() => {
              let index = 0;
              return () => ++index;
            })(),
            wizard: {} as any,
          })}
        </div>,
      );

    let taskDefinition = renderPages()
      .find(WizardPage)
      .filterWhere((page) => page.prop('label') === 'Task Definition')
      .prop('render')({ innerRef: React.createRef(), onLoadingChanged: () => undefined }).props.children;
    taskDefinition.props.onFieldChange('useTaskDefinitionArtifact', false);
    expect(formik.values.serviceDiscoveryAssociations[0].containerName).toBeNull();
    expect(
      renderPages()
        .find(WizardPage)
        .someWhere((page) => page.prop('label') === 'Container'),
    ).toBe(true);

    taskDefinition = renderPages()
      .find(WizardPage)
      .filterWhere((page) => page.prop('label') === 'Task Definition')
      .prop('render')({ innerRef: React.createRef(), onLoadingChanged: () => undefined }).props.children;
    taskDefinition.props.onFieldChange('useTaskDefinitionArtifact', true);
    expect(formik.values.serviceDiscoveryAssociations[0].containerName).toBe('');
    expect(
      renderPages()
        .find(WizardPage)
        .someWhere((page) => page.prop('label') === 'Container'),
    ).toBe(false);
    expect(formik.setValues).toHaveBeenCalledTimes(2);

    wizardModal.prop('closeModal')(formik.values);
    expect(wrapper.instance().props.closeModal).toHaveBeenCalledWith(formik.values);
  });

  it('composes sequential updates before asynchronous Formik values settle', () => {
    const command = buildCommand({ preferSourceCapacity: false, useSourceCapacity: false });
    const modal = buildUnrenderedModal(command) as any;
    const formik = {
      setFieldValue: jasmine.createSpy('setFieldValue'),
      setValues: jasmine.createSpy('setValues'),
      values: { ...command },
    } as any;

    modal.updateCommand(formik, 'useSourceCapacity', true);
    modal.updateCommand(formik, 'preferSourceCapacity', true);

    expect(modal.state.command.useSourceCapacity).toBe(true);
    expect(modal.state.command.preferSourceCapacity).toBe(true);
    expect(formik.setValues.calls.mostRecent().args[0]).toEqual(modal.state.command);
  });

  ['create', 'clone'].forEach((mode) => {
    it(`submits ad-hoc ${mode} commands through TaskMonitor`, () => {
      const command = buildCommand({ viewState: { contextImages: [], dirty: {}, mode } as any });
      const props = buildProps(command);
      const modal = new EcsCloneServerGroupModal(props) as any;
      const task = Promise.resolve({ id: 'task-id' });
      const cloneServerGroup = jasmine.createSpy('cloneServerGroup').and.returnValue(task as any);
      modal.context = { services: { serverGroupWriter: { cloneServerGroup } } };
      const monitorSubmit = spyOn(
        modal.state.taskMonitor,
        'submit',
      ).and.callFake((submitMethod: () => PromiseLike<any>) => submitMethod());

      modal.submit();

      expect(monitorSubmit).toHaveBeenCalled();
      expect(cloneServerGroup).toHaveBeenCalledWith(modal.state.command, props.application);
      expect(props.closeModal).not.toHaveBeenCalled();
    });
  });

  it('refreshes server groups and navigates to the created group from task and command identity', () => {
    const command = buildCommand({
      credentials: 'ecs-account',
      region: 'eu-west-1',
      viewState: { contextImages: [], dirty: {}, mode: 'create' } as any,
    });
    const props = buildProps(command);
    const modal = new EcsCloneServerGroupModal(props) as any;
    modal.state.taskMonitor.task = {
      execution: {
        stages: [
          {
            context: { 'deploy.server.groups': { 'eu-west-1': 'app-main-v042' } },
            type: 'cloneServerGroup',
          },
        ],
      },
    };
    const state = {
      go: jasmine.createSpy('go'),
      includes: jasmine.createSpy('includes').and.callFake((name: string) => name === '**.clusters'),
    };
    props.stateService = state;

    modal.onTaskComplete();

    expect(props.application.serverGroups.refresh).toHaveBeenCalled();
    expect(props.application.serverGroups.onNextRefresh).toHaveBeenCalledWith(null, modal.onApplicationRefresh);

    const refreshCallback = props.application.serverGroups.onNextRefresh.calls.mostRecent().args[1];
    refreshCallback();

    expect(state.includes).toHaveBeenCalledWith('**.clusters');
    expect(state.go).toHaveBeenCalledWith('.serverGroup', {
      accountId: 'ecs-account',
      provider: 'ecs',
      region: 'eu-west-1',
      serverGroup: 'app-main-v042',
    });
  });

  it('retains modal command state when ad-hoc submission fails', async () => {
    const command = buildCommand({ viewState: { contextImages: [], dirty: {}, mode: 'create' } as any });
    const props = buildProps(command);
    const modal = new EcsCloneServerGroupModal(props) as any;
    const cloneServerGroup = jasmine
      .createSpy('cloneServerGroup')
      .and.callFake(() => Promise.reject({ failureMessage: 'create failed' }) as any);
    modal.context = { services: { serverGroupWriter: { cloneServerGroup } } };

    modal.submit();
    await Promise.resolve();
    await Promise.resolve();

    expect(modal.state.taskMonitor.error).toBe(true);
    expect(modal.state.taskMonitor.errorMessage).toBe('create failed');
    expect(modal.state.command).toBe(command);
    expect(props.closeModal).not.toHaveBeenCalled();
    expect(props.dismissModal).not.toHaveBeenCalled();
  });

  it('renders task submission and failure status inside the modal', () => {
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(buildCommand())} />, {
      disableLifecycleMethods: true,
    } as any);

    expect(wrapper.find(WizardModal).prop('taskMonitor')).toBe(wrapper.state('taskMonitor'));
  });

  it('renders the legacy eight-page WizardModal grouping', () => {
    const command = buildCommand({ useTaskDefinitionArtifact: false });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);
    wrapper.setState({ loaded: true });

    const wizardModal = wrapper.find(WizardModal);
    expect(wizardModal.exists()).toBe(true);

    const pageTree = shallow(
      <div>
        {wizardModal.prop('render')({
          formik: { values: command } as any,
          nextIdx: (() => {
            let index = 0;
            return () => ++index;
          })(),
          wizard: {} as any,
        })}
      </div>,
    );

    expect(pageTree.find(WizardPage).map((page) => page.prop('label'))).toEqual([
      'Basic Settings',
      'Networking',
      'Task Definition',
      'Container',
      'Horizontal Scaling',
      'Logging',
      'Service Discovery',
      'Advanced Settings',
    ]);
  });

  it('validates required location, task source, and populated mapping fields', () => {
    const command = buildCommand({
      capacity: { desired: 1, max: 2, min: 0 },
      credentials: '',
      ecsClusterName: '',
      freeFormDetails: '',
      region: '',
      stack: '',
      taskDefinitionArtifact: {},
      useTaskDefinitionArtifact: true,
    });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);
    const validate = wrapper.find(WizardModal).prop('validate');

    const requiredErrors = validate(command);
    expect(requiredErrors.credentials).toBeTruthy();
    expect(requiredErrors.region).toBeTruthy();
    expect(requiredErrors.ecsClusterName).toBeTruthy();
    expect(requiredErrors.taskDefinitionArtifact).toBeTruthy();

    const artifactMappingErrors = validate({
      ...command,
      containerMappings: [{ containerName: '', imageDescription: {} }],
      credentials: 'account',
      ecsClusterName: 'cluster',
      region: 'eu-west-1',
      serviceDiscoveryAssociations: [{ containerName: '', containerPort: NaN, registry: {} }],
      targetGroupMappings: [{ containerName: '', containerPort: NaN, targetGroup: '' }],
      taskDefinitionArtifact: { artifactId: 'artifact-id' },
    } as any);
    expect(artifactMappingErrors.containerMappings[0].containerName).toBeTruthy();
    expect(artifactMappingErrors.containerMappings[0].imageDescription).toBeTruthy();
    expect(artifactMappingErrors.targetGroupMappings[0].containerName).toBeTruthy();
    expect(artifactMappingErrors.targetGroupMappings[0].targetGroup).toBeTruthy();
    expect(artifactMappingErrors.targetGroupMappings[0].containerPort).toBeTruthy();
    expect(artifactMappingErrors.serviceDiscoveryAssociations[0].containerName).toBeTruthy();
    expect(artifactMappingErrors.serviceDiscoveryAssociations[0].registry).toBeTruthy();
    expect(artifactMappingErrors.serviceDiscoveryAssociations[0].containerPort).toBeTruthy();

    const inputErrors = validate({
      ...command,
      credentials: 'account',
      ecsClusterName: 'cluster',
      imageDescription: {},
      region: 'eu-west-1',
      serviceDiscoveryAssociations: [{ containerName: null, containerPort: NaN, registry: {} }],
      targetGroupMappings: [{ containerName: null, containerPort: NaN, targetGroup: '' }],
      useTaskDefinitionArtifact: false,
    } as any);
    expect(inputErrors.imageDescription.imageId).toBeTruthy();
    expect(inputErrors.targetGroupMappings[0].containerName).toBeUndefined();
    expect(inputErrors.targetGroupMappings[0].targetGroup).toBeTruthy();
    expect(inputErrors.serviceDiscoveryAssociations[0].containerName).toBeUndefined();
  });

  it('validates naming patterns and finite ordered capacity through page refs', () => {
    const command = buildCommand({
      capacity: { desired: Infinity, max: 1.5, min: NaN },
      credentials: 'account',
      ecsClusterName: 'cluster',
      freeFormDetails: 'detail!',
      region: 'eu-west-1',
      stack: 'main-stack',
      taskDefinitionArtifact: { artifactId: 'artifact-id' },
      useTaskDefinitionArtifact: true,
    });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);
    const wizardModal = wrapper.find(WizardModal);
    const validate = wizardModal.prop('validate');
    const errors = validate(command);
    expect(errors.stack).toBeTruthy();
    expect(errors.freeFormDetails).toBeTruthy();
    expect(errors.capacity.min).toBeTruthy();
    expect(errors.capacity.desired).toBeTruthy();
    expect(errors.capacity.max).toBeTruthy();

    const validCommand = {
      ...command,
      capacity: { desired: 2, max: 3, min: 1 },
      freeFormDetails: 'detail-one_1.0',
      launchType: 'FARGATE',
      stack: 'main_stack.1',
    } as IEcsServerGroupCommand;
    expect(validate(validCommand)).toEqual({});

    const relationshipErrors = validate({ ...validCommand, capacity: { desired: 4, max: 3, min: 2 } });
    expect(relationshipErrors.capacity.desired).toBeTruthy();
    const reversedErrors = validate({ ...validCommand, capacity: { desired: 2, max: 1, min: 3 } });
    expect(reversedErrors.capacity.min).toBeTruthy();

    const pageTree = shallow(
      <div>
        {wizardModal.prop('render')({
          formik: { values: validCommand } as any,
          nextIdx: (() => {
            let index = 0;
            return () => ++index;
          })(),
          wizard: {
            onWizardPageAdded: () => undefined,
            onWizardPageRemoved: () => undefined,
            onWizardPageStateChanged: () => undefined,
          },
        })}
      </div>,
    );
    const capacityPage = pageTree
      .find(WizardPage)
      .filterWhere((page) => page.prop('label') === 'Horizontal Scaling')
      .getElement();
    const mountedPage = mount(capacityPage);
    const pageErrors = (mountedPage.instance() as WizardPage<IEcsServerGroupCommand>).validate(command);
    expect(pageErrors.capacity.min).toBeTruthy();
    expect(pageErrors.capacity.desired).toBeTruthy();
    expect(pageErrors.capacity.max).toBeTruthy();
    mountedPage.unmount();
  });

  it('requires a launch type in launch type compute mode', () => {
    const command = buildCommand({
      capacity: { desired: 1, max: 2, min: 0 },
      computeOption: 'launchType',
      credentials: 'account',
      ecsClusterName: 'cluster',
      launchType: '',
      region: 'eu-west-1',
      taskDefinitionArtifact: { artifactId: 'artifact-id' },
      useTaskDefinitionArtifact: true,
    });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);
    const validate = wrapper.find(WizardModal).prop('validate');

    expect(validate(command).launchType).toBeTruthy();
    expect(validate({ ...command, launchType: 'FARGATE' }).launchType).toBeUndefined();
  });

  it('requires a usable default strategy in default capacity provider mode', () => {
    const command = buildCommand({
      backingData: {
        ...buildCommand().backingData,
        filtered: { ...buildCommand().backingData.filtered, defaultCapacityProviderStrategy: [] },
      },
      capacity: { desired: 1, max: 2, min: 0 },
      computeOption: 'capacityProviders',
      credentials: 'account',
      ecsClusterName: 'cluster',
      region: 'eu-west-1',
      taskDefinitionArtifact: { artifactId: 'artifact-id' },
      useDefaultCapacityProviders: true,
      useTaskDefinitionArtifact: true,
    });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);
    const validate = wrapper.find(WizardModal).prop('validate');

    expect(validate(command).capacityProviderStrategy).toBeTruthy();
    expect(
      validate({
        ...command,
        backingData: {
          ...command.backingData,
          filtered: {
            ...command.backingData.filtered,
            defaultCapacityProviderStrategy: [{ base: -1, capacityProvider: '', weight: NaN }],
          },
        },
      }).capacityProviderStrategy,
    ).toBeTruthy();
    expect(
      validate({
        ...command,
        backingData: {
          ...command.backingData,
          filtered: {
            ...command.backingData.filtered,
            defaultCapacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }],
          },
        },
      }).capacityProviderStrategy,
    ).toBeUndefined();
  });

  it('validates every custom capacity provider strategy row', () => {
    const command = buildCommand({
      capacity: { desired: 1, max: 2, min: 0 },
      capacityProviderStrategy: [],
      computeOption: 'capacityProviders',
      credentials: 'account',
      ecsClusterName: 'cluster',
      region: 'eu-west-1',
      taskDefinitionArtifact: { artifactId: 'artifact-id' },
      useDefaultCapacityProviders: false,
      useTaskDefinitionArtifact: true,
    });
    const wrapper = shallow(<EcsCloneServerGroupModal {...buildProps(command)} />, {
      disableLifecycleMethods: true,
    } as any);
    const validate = wrapper.find(WizardModal).prop('validate');

    expect(validate(command).capacityProviderStrategy).toBeTruthy();

    const errors = validate({
      ...command,
      capacityProviderStrategy: [
        { base: -1, capacityProvider: '', weight: Infinity },
        { base: 1.5, capacityProvider: 'FARGATE', weight: NaN },
      ],
    } as any).capacityProviderStrategy;
    expect(errors[0].capacityProvider).toBeTruthy();
    expect(errors[0].base).toBeTruthy();
    expect(errors[0].weight).toBeTruthy();
    expect(errors[1].capacityProvider).toBeUndefined();
    expect(errors[1].base).toBeTruthy();
    expect(errors[1].weight).toBeTruthy();

    expect(
      validate({
        ...command,
        capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }],
      }).capacityProviderStrategy,
    ).toBeUndefined();
  });

  it('does not submit the wizard from add, remove, or option buttons', () => {
    const command = buildCommand({
      backingData: {
        filtered: {
          availableCapacityProviders: ['FARGATE'],
          defaultCapacityProviderStrategy: [],
          images: [],
          serviceDiscoveryRegistries: [{ displayName: 'registry', id: 'registry' }],
          targetGroups: ['target-group'],
        },
        networkModes: [],
      } as any,
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }],
      credentials: 'account',
      ecsClusterName: 'cluster',
      placementConstraints: [{}],
      region: 'eu-west-1',
      serviceDiscoveryAssociations: [
        { containerName: 'api', containerPort: 8080, registry: { displayName: 'registry', id: 'registry' } } as any,
      ],
      targetGroupMappings: [{ containerName: 'api', containerPort: 8080, targetGroup: 'target-group' }],
      useDefaultCapacityProviders: false,
      useTaskDefinitionArtifact: true,
      viewState: { contextImages: [], currentStage: {}, dirty: {}, pipeline: {} } as any,
    });
    const childProps = {
      command,
      configureCommand: () => Promise.resolve(),
      notifyAngular: jasmine.createSpy('notifyAngular'),
    };
    const capacityProvider = shallow(<EcsCapacityProvider {...childProps} />, {
      disableLifecycleMethods: true,
    } as any);
    capacityProvider.setState({ activeCapacityProviderIndex: 0, capacityProviderLoadedFlag: true });
    const wrappers = [
      shallow(<Container {...childProps} />, { disableLifecycleMethods: true } as any),
      shallow(<TaskDefinition {...childProps} />, { disableLifecycleMethods: true } as any),
      shallow(<ServiceDiscovery {...childProps} />, { disableLifecycleMethods: true } as any),
      capacityProvider,
      shallow(
        <AdvancedSettings
          application={buildProps(command).application}
          command={command}
          configureCommand={() => Promise.resolve()}
          onFieldChange={jasmine.createSpy('onFieldChange')}
        />,
      ),
    ];

    const buttons = wrappers.flatMap((wrapper) => wrapper.find('button').getElements());
    expect(buttons.length).toBeGreaterThan(0);
    buttons.forEach((button) => expect(button.props.type).toBe('button'));
  });

  it('restores Basic Settings account, region, cluster, naming, and strategy controls', () => {
    const command = buildCommand({
      backingData: {
        accounts: ['account-a', 'account-b'],
        filtered: {
          ecsClusters: ['available-cluster', 'persisted-cluster'],
          regions: [{ name: 'eu-west-1' }, { name: 'us-east-1' }],
        },
      } as any,
      credentials: 'account-a',
      ecsClusterName: 'persisted-cluster',
      freeFormDetails: 'api',
      region: 'eu-west-1',
      selectedProvider: 'ecs',
      stack: 'prod',
      viewState: { contextImages: [], dirty: {}, disableStrategySelection: false } as any,
    });
    const onFieldChange = jasmine.createSpy('onFieldChange');
    const wrapper = shallow(
      React.createElement(BasicSettings as any, {
        application: buildProps(command).application,
        command,
        onFieldChange,
      }),
    );

    expect(wrapper.find(AccountSelectInput).prop('value')).toBe('account-a');
    expect(wrapper.find(RegionSelectInput).prop('value')).toBe('eu-west-1');
    expect(findByTestId(wrapper, 'ServerGroup.clusterName').prop('value')).toBe('persisted-cluster');
    expect(findByTestId(wrapper, 'ServerGroup.stack').prop('value')).toBe('prod');
    expect(findByTestId(wrapper, 'ServerGroup.details').prop('value')).toBe('api');
    expect(wrapper.find(DeploymentStrategySelector).exists()).toBe(true);

    wrapper.find(AccountSelectInput).prop('onChange')({ target: { value: 'account-b' } } as any);
    wrapper.find(RegionSelectInput).prop('onChange')({ target: { value: 'us-east-1' } } as any);
    findByTestId(wrapper, 'ServerGroup.clusterName').simulate('change', { target: { value: 'available-cluster' } });

    expect(onFieldChange).toHaveBeenCalledWith('credentials', 'account-b');
    expect(onFieldChange).toHaveBeenCalledWith('region', 'us-east-1');
    expect(onFieldChange).toHaveBeenCalledWith('ecsClusterName', 'available-cluster');
  });

  it('restores Networking VPC subnet and security-group controls without hiding persisted references', () => {
    const command = buildCommand({
      associatePublicIpAddress: false,
      backingData: {
        filtered: {
          securityGroupNames: ['available-sg'],
          subnetTypes: [{ purpose: 'available-subnet', vpcId: 'vpc-1' }],
        },
        networkModes: ['bridge', 'awsvpc'],
      } as any,
      networkMode: 'awsvpc',
      securityGroupNames: ['persisted-sg'],
      subnetTypes: ['persisted-subnet'],
    });
    const onFieldChange = jasmine.createSpy('onFieldChange');
    const configureCommand = jasmine.createSpy('configureCommand').and.returnValue(Promise.resolve());
    const page = shallow(
      React.createElement(NetworkingSettings as any, {
        application: buildProps(command).application,
        command,
        configureCommand,
        onFieldChange,
      }),
    );

    expect(page.find(EcsNetworking).exists()).toBe(true);
    const networking = shallow(page.find(EcsNetworking).getElement());
    const subnetOptions = findByTestId(networking, 'Networking.subnetType').find(TetheredSelect).prop('options');
    const securityGroupOptions = findByTestId(networking, 'Networking.securityGroups')
      .find(TetheredSelect)
      .prop('options');

    expect(subnetOptions.map((option: any) => option.value)).toEqual(['available-subnet', 'persisted-subnet']);
    expect(securityGroupOptions.map((option: any) => option.value)).toEqual(['available-sg', 'persisted-sg']);
  });

  it('restores Task Definition source, artifact mappings, and persisted target groups', () => {
    const command = buildCommand({
      backingData: {
        filtered: {
          images: [],
          targetGroups: ['available-target'],
        },
      } as any,
      containerMappings: [],
      targetGroupMappings: [{ containerName: 'api', containerPort: 8080, targetGroup: 'persisted-target' }],
      taskDefinitionArtifact: {},
      useTaskDefinitionArtifact: true,
      viewState: {
        contextImages: [],
        currentStage: {},
        dirty: {},
        mode: 'editPipeline',
        pipeline: {},
      } as any,
    });
    const onFieldChange = jasmine.createSpy('onFieldChange');
    const configureCommand = jasmine.createSpy('configureCommand').and.returnValue(Promise.resolve());
    const page = shallow(
      React.createElement(TaskDefinitionSettings as any, {
        application: buildProps(command).application,
        command,
        configureCommand,
        onFieldChange,
      }),
    );

    expect(findByTestId(page, 'ServerGroup.useInputs').prop('checked')).toBe(false);
    expect(findByTestId(page, 'ServerGroup.useArtifacts').prop('checked')).toBe(true);
    expect(page.find(TaskDefinition).exists()).toBe(true);

    findByTestId(page, 'ServerGroup.useInputs').simulate('change');
    expect(onFieldChange).toHaveBeenCalledWith('useTaskDefinitionArtifact', false);

    const taskDefinition = shallow(page.find(TaskDefinition).getElement());
    const targetOptions = findByTestId(taskDefinition, 'Artifacts.targetGroup').find(TetheredSelect).prop('options');
    expect(targetOptions.map((option: any) => option.value)).toEqual(['available-target', 'persisted-target']);
  });

  it('restores Container image, resources, and persisted target-group mappings', () => {
    const command = buildCommand({
      backingData: {
        filtered: {
          images: [{ imageId: 'registry/api:latest' }],
          targetGroups: ['available-target'],
        },
      } as any,
      computeUnits: 512,
      imageDescription: { imageId: 'registry/api:latest' } as any,
      reservedMemory: 1024,
      targetGroupMappings: [{ containerName: '', containerPort: 8080, targetGroup: 'persisted-target' }],
    });
    const onFieldChange = jasmine.createSpy('onFieldChange');
    const page = shallow(
      React.createElement(ContainerSettings as any, {
        application: buildProps(command).application,
        command,
        configureCommand: jasmine.createSpy('configureCommand').and.returnValue(Promise.resolve()),
        onFieldChange,
      }),
    );

    expect(page.find(Container).exists()).toBe(true);
    const container = shallow(page.find(Container).getElement());
    expect(findByTestId(container, 'ContainerInputs.containerImage').exists()).toBe(true);
    expect(findByTestId(container, 'ContainerInputs.computeUnits').prop('value')).toBe(512);
    expect(findByTestId(container, 'ContainerInputs.reservedMemory').prop('value')).toBe(1024);
    const targetOptions = findByTestId(container, 'ContainerInputs.targetGroup').find(TetheredSelect).prop('options');
    expect(targetOptions.map((option: any) => option.value)).toEqual(['available-target', 'persisted-target']);
  });

  it('restores Horizontal Scaling compute mode, capacity, and source-policy controls', () => {
    const command = buildCommand({
      capacity: { desired: 3, max: 5, min: 2 },
      computeOption: 'launchType',
      copySourceScalingPoliciesAndActions: true,
      launchType: 'FARGATE',
      useSourceCapacity: false,
    });
    const onFieldChange = jasmine.createSpy('onFieldChange');
    const page = shallow(
      React.createElement(HorizontalScalingSettings as any, {
        application: buildProps(command).application,
        command,
        configureCommand: jasmine.createSpy('configureCommand').and.returnValue(Promise.resolve()),
        onFieldChange,
      }),
    );

    expect(findByTestId(page, 'ServerGroup.computeOptionsLaunchType').prop('checked')).toBe(true);
    expect(findByTestId(page, 'ServerGroup.launchType').prop('value')).toBe('FARGATE');
    expect(findByTestId(page, 'ServerGroup.capacity.desired').prop('value')).toBe(3);
    expect(findByTestId(page, 'ServerGroup.capacity.min').prop('value')).toBe(2);
    expect(findByTestId(page, 'ServerGroup.capacity.max').prop('value')).toBe(5);
    expect(findByTestId(page, 'ServerGroup.useSourceCapacity').prop('checked')).toBe(false);
    expect(findByTestId(page, 'ServerGroup.copySourceScalingPoliciesAndActions').prop('checked')).toBe(true);

    findByTestId(page, 'ServerGroup.computeOptionsCapacityProviders').simulate('change');
    expect(onFieldChange).toHaveBeenCalledWith('computeOption', 'capacityProviders');
    expect(onFieldChange).toHaveBeenCalledWith('launchType', '');

    const capacityProviderPage = shallow(
      React.createElement(HorizontalScalingSettings as any, {
        application: buildProps(command).application,
        command: { ...command, computeOption: 'capacityProviders' },
        configureCommand: jasmine.createSpy('configureCommand').and.returnValue(Promise.resolve()),
        onFieldChange,
      }),
    );
    expect(capacityProviderPage.find(EcsCapacityProvider).exists()).toBe(true);
  });

  it('restores Logging driver and option-map controls', () => {
    const command = buildCommand({ logDriver: 'awslogs', logOptions: { 'awslogs-region': 'eu-west-1' } });
    const onFieldChange = jasmine.createSpy('onFieldChange');
    const page = shallow(
      React.createElement(LoggingSettings as any, {
        application: buildProps(command).application,
        command,
        onFieldChange,
      }),
    );

    expect(findByTestId(page, 'Logging.logDriver').prop('value')).toBe('awslogs');
    expect(page.find(MapEditor).prop('model')).toEqual({ 'awslogs-region': 'eu-west-1' });

    findByTestId(page, 'Logging.logDriver').simulate('change', { target: { value: 'fluentd' } });
    page.find(MapEditor).prop('onChange')({ address: 'localhost:24224' }, true);
    expect(onFieldChange).toHaveBeenCalledWith('logDriver', 'fluentd');
    expect(onFieldChange).toHaveBeenCalledWith('logOptions', { address: 'localhost:24224' });
  });

  it('restores Service Discovery mappings without hiding persisted registries', () => {
    const availableRegistry = { displayName: 'available (registry-1)', id: 'registry-1' };
    const persistedRegistry = { displayName: 'persisted (registry-2)', id: 'registry-2' };
    const command = buildCommand({
      backingData: { filtered: { serviceDiscoveryRegistries: [availableRegistry] } } as any,
      serviceDiscoveryAssociations: [{ containerName: 'api', containerPort: 8080, registry: persistedRegistry } as any],
      useTaskDefinitionArtifact: true,
    });
    const page = shallow(
      React.createElement(ServiceDiscoverySettings as any, {
        application: buildProps(command).application,
        command,
        configureCommand: jasmine.createSpy('configureCommand').and.returnValue(Promise.resolve()),
        onFieldChange: jasmine.createSpy('onFieldChange'),
      }),
    );

    expect(page.find(ServiceDiscovery).exists()).toBe(true);
    const serviceDiscovery = shallow(page.find(ServiceDiscovery).getElement());
    const registryOptions = findByTestId(serviceDiscovery, 'ServiceDiscovery.registry')
      .find(TetheredSelect)
      .prop('options');
    expect(registryOptions.map((option: any) => option.value)).toEqual([
      'available (registry-1)',
      'persisted (registry-2)',
    ]);
    expect(findByTestId(serviceDiscovery, 'ServiceDiscovery.containerName').prop('value')).toBe('api');
    expect(findByTestId(serviceDiscovery, 'ServiceDiscovery.containerPort').prop('value')).toBe(8080);
  });

  it('refreshes Networking options from rerendered command props without clobbering selections', () => {
    const command = buildCommand({
      associatePublicIpAddress: false,
      backingData: {
        filtered: {
          securityGroupNames: ['old-sg'],
          subnetTypes: [{ purpose: 'old-subnet', vpcId: 'vpc-old' }],
        },
        networkModes: ['awsvpc'],
      } as any,
      networkMode: 'awsvpc',
      securityGroupNames: ['persisted-sg'],
      subnetTypes: ['persisted-subnet'],
    });
    const wrapper = shallow(
      <EcsNetworking
        command={command}
        configureCommand={() => new Promise<void>(() => undefined)}
        notifyAngular={jasmine.createSpy('notifyAngular')}
      />,
    );

    command.backingData.filtered.securityGroupNames = ['new-sg'];
    command.backingData.filtered.subnetTypes = [{ purpose: 'new-subnet', vpcId: 'vpc-new' } as any];
    wrapper.setProps({ command });

    expect(wrapper.state('securityGroupNames')).toEqual(['persisted-sg']);
    expect(wrapper.state('subnetTypes')).toEqual(['persisted-subnet']);
    expect(wrapper.state('securityGroupsAvailable')).toEqual(['new-sg', 'persisted-sg']);
    expect((wrapper.state('subnetTypesAvailable') as any[]).map((subnet) => subnet.purpose)).toEqual([
      'new-subnet',
      'persisted-subnet',
    ]);
  });

  it('refreshes Container options from rerendered command props without clobbering selections', () => {
    const persistedImage = { imageId: 'registry/persisted:latest' } as any;
    const command = buildCommand({
      backingData: { filtered: { images: [], targetGroups: ['old-target'] } } as any,
      imageDescription: persistedImage,
      targetGroupMappings: [{ containerName: '', containerPort: 8080, targetGroup: 'persisted-target' }],
    });
    const wrapper = shallow(
      <Container
        command={command}
        configureCommand={() => new Promise<void>(() => undefined)}
        notifyAngular={jasmine.createSpy('notifyAngular')}
      />,
    );

    command.backingData.filtered.images = [{ imageId: 'registry/new:latest' } as any];
    command.backingData.filtered.targetGroups = ['new-target'];
    wrapper.setProps({ command });

    expect(wrapper.state('imageDescription')).toBe(persistedImage);
    expect(wrapper.state('targetGroupMappings')).toEqual(command.targetGroupMappings);
    expect((wrapper.state('dockerImages') as any[]).map((image) => image.imageId)).toEqual(['registry/new:latest']);
    expect(wrapper.state('targetGroupsAvailable')).toEqual(['new-target', 'persisted-target']);
  });

  it('refreshes TaskDefinition options from rerendered command props without clobbering selections', () => {
    const persistedArtifact = { artifactId: 'expected-artifact' };
    const command = buildCommand({
      backingData: { filtered: { images: [], targetGroups: ['old-target'] } } as any,
      containerMappings: [{ containerName: 'api', imageDescription: { imageId: 'registry/persisted:latest' } as any }],
      targetGroupMappings: [{ containerName: 'api', containerPort: 8080, targetGroup: 'persisted-target' }],
      taskDefinitionArtifact: persistedArtifact,
    });
    const wrapper = shallow(
      <TaskDefinition
        command={command}
        configureCommand={() => new Promise<void>(() => undefined)}
        notifyAngular={jasmine.createSpy('notifyAngular')}
      />,
    );

    command.backingData.filtered.images = [{ imageId: 'registry/new:latest' } as any];
    command.backingData.filtered.targetGroups = ['new-target'];
    wrapper.setProps({ command });

    expect(wrapper.state('taskDefArtifact')).toBe(persistedArtifact);
    expect(wrapper.state('containerMappings')).toEqual(command.containerMappings);
    expect(wrapper.state('targetGroupMappings')).toEqual(command.targetGroupMappings);
    expect((wrapper.state('dockerImages') as any[]).map((image) => image.imageId)).toEqual(['registry/new:latest']);
    expect(wrapper.state('targetGroupsAvailable')).toEqual(['new-target', 'persisted-target']);
  });

  it('refreshes ServiceDiscovery options from rerendered command props without clobbering selections', () => {
    const persistedRegistry = { displayName: 'persisted (registry-1)', id: 'registry-1' };
    const associations = [{ containerName: 'api', containerPort: 8080, registry: persistedRegistry } as any];
    const command = buildCommand({
      backingData: {
        filtered: { serviceDiscoveryRegistries: [{ displayName: 'old (registry-2)', id: 'registry-2' }] },
      } as any,
      serviceDiscoveryAssociations: associations,
      useTaskDefinitionArtifact: true,
    });
    const wrapper = shallow(
      <ServiceDiscovery
        command={command}
        configureCommand={() => new Promise<void>(() => undefined)}
        notifyAngular={jasmine.createSpy('notifyAngular')}
      />,
    );

    command.backingData.filtered.serviceDiscoveryRegistries = [
      { displayName: 'new (registry-3)', id: 'registry-3' } as any,
    ];
    wrapper.setProps({ command });

    expect(wrapper.state('serviceDiscoveryAssociations')).toBe(associations);
    expect(
      (wrapper.state('serviceDiscoveryRegistriesAvailable') as any[]).map((registry) => registry.displayName),
    ).toEqual(['new (registry-3)', 'persisted (registry-1)']);
  });

  it('normalizes ServiceDiscovery container names when switching away from task definition artifacts', () => {
    const registry = { displayName: 'persisted (registry-1)', id: 'registry-1' };
    const command = buildCommand({
      serviceDiscoveryAssociations: [{ containerName: 'api', containerPort: 8080, registry } as any],
      useTaskDefinitionArtifact: true,
    });
    const notifyAngular = jasmine.createSpy('notifyAngular');
    const wrapper = shallow(
      <ServiceDiscovery
        command={command}
        configureCommand={() => new Promise<void>(() => undefined)}
        notifyAngular={notifyAngular}
      />,
    );

    command.useTaskDefinitionArtifact = false;
    wrapper.setProps({ command });

    expect(wrapper.state('useTaskDefinitionArtifact')).toBe(false);
    expect(wrapper.state('serviceDiscoveryAssociations')).toEqual([
      { containerName: null, containerPort: 8080, registry } as any,
    ]);
    expect(findByTestId(wrapper, 'ServiceDiscovery.containerName').exists()).toBe(false);
    expect(notifyAngular).toHaveBeenCalledWith(
      'serviceDiscoveryAssociations',
      wrapper.state('serviceDiscoveryAssociations'),
    );
  });

  it('preserves and normalizes ServiceDiscovery container names when switching to task definition artifacts', () => {
    const registry = { displayName: 'persisted (registry-1)', id: 'registry-1' };
    const command = buildCommand({
      serviceDiscoveryAssociations: [{ containerName: null, containerPort: 8080, registry } as any],
      useTaskDefinitionArtifact: false,
    });
    const notifyAngular = jasmine.createSpy('notifyAngular');
    const wrapper = shallow(
      <ServiceDiscovery
        command={command}
        configureCommand={() => new Promise<void>(() => undefined)}
        notifyAngular={notifyAngular}
      />,
    );

    command.serviceDiscoveryAssociations = [
      { containerName: null, containerPort: 8080, registry } as any,
      { containerName: 'worker', containerPort: 9090, registry } as any,
    ];
    command.useTaskDefinitionArtifact = true;
    wrapper.setProps({ command });

    expect(wrapper.state('useTaskDefinitionArtifact')).toBe(true);
    expect(
      (wrapper.state('serviceDiscoveryAssociations') as any[]).map((association) => association.containerName),
    ).toEqual(['', 'worker']);
    expect(findByTestId(wrapper, 'ServiceDiscovery.containerName').map((input) => input.prop('value'))).toEqual([
      '',
      'worker',
    ]);
    expect(notifyAngular).toHaveBeenCalledWith(
      'serviceDiscoveryAssociations',
      wrapper.state('serviceDiscoveryAssociations'),
    );
  });

  it('refreshes CapacityProvider options from rerendered command props without clobbering selections', () => {
    const strategy = [{ base: 0, capacityProvider: 'persisted-provider', weight: 1 }];
    const command = buildCommand({
      backingData: {
        filtered: {
          availableCapacityProviders: ['old-provider'],
          defaultCapacityProviderStrategy: [],
        },
      } as any,
      capacityProviderStrategy: strategy,
      ecsClusterName: 'old-cluster',
      useDefaultCapacityProviders: false,
    });
    const wrapper = shallow(
      <EcsCapacityProvider
        command={command}
        configureCommand={() => new Promise<void>(() => undefined)}
        notifyAngular={jasmine.createSpy('notifyAngular')}
      />,
    );

    command.backingData.filtered.availableCapacityProviders = ['new-provider'];
    command.ecsClusterName = 'new-cluster';
    wrapper.setProps({ command });

    expect(wrapper.state('capacityProviderStrategy')).toBe(strategy);
    expect(wrapper.state('availableCapacityProviders')).toEqual(['new-provider', 'persisted-provider']);
    expect(wrapper.state('ecsClusterName')).toBe('new-cluster');
  });

  it('publishes the refreshed cluster strategy from the setState callback', async () => {
    const refreshedStrategy = [{ base: 1, capacityProvider: 'FARGATE_SPOT', weight: 2 }];
    const command = buildCommand({
      backingData: {
        filtered: {
          availableCapacityProviders: [],
          defaultCapacityProviderStrategy: [],
        },
      } as any,
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }],
      useDefaultCapacityProviders: true,
    });
    const notifyAngular = jasmine.createSpy('notifyAngular');
    const configureCommand = jasmine.createSpy('configureCommand').and.callFake(() => {
      command.backingData.filtered.defaultCapacityProviderStrategy = refreshedStrategy;
      return Promise.resolve();
    });
    const wrapper = shallow(
      <EcsCapacityProvider command={command} configureCommand={configureCommand} notifyAngular={notifyAngular} />,
      { disableLifecycleMethods: true } as any,
    );
    const instance = wrapper.instance() as any;
    let applyState: () => void;
    spyOn(instance, 'setState').and.callFake((state: any, callback?: () => void) => {
      applyState = () => {
        instance.state = { ...instance.state, ...state };
        callback?.();
      };
    });

    instance.componentDidMount();
    await Promise.resolve();
    expect(notifyAngular).not.toHaveBeenCalledWith('capacityProviderStrategy', jasmine.anything());

    applyState();
    expect(notifyAngular).toHaveBeenCalledWith('capacityProviderStrategy', refreshedStrategy);
  });

  it('publishes refreshed default strategy props once without a notification loop', () => {
    const originalStrategy = [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }];
    const refreshedStrategy = [{ base: 1, capacityProvider: 'FARGATE_SPOT', weight: 2 }];
    const command = buildCommand({
      backingData: {
        filtered: {
          availableCapacityProviders: ['FARGATE', 'FARGATE_SPOT'],
          defaultCapacityProviderStrategy: originalStrategy,
        },
      } as any,
      capacityProviderStrategy: originalStrategy,
      ecsClusterName: 'cluster-a',
      useDefaultCapacityProviders: true,
    });
    const notifyAngular = jasmine.createSpy('notifyAngular').and.callFake((field: string, value: any) => {
      command[field] = value;
    });
    const wrapper = shallow(
      <EcsCapacityProvider
        command={command}
        configureCommand={() => new Promise<void>(() => undefined)}
        notifyAngular={notifyAngular}
      />,
      { disableLifecycleMethods: true } as any,
    );

    command.backingData.filtered.defaultCapacityProviderStrategy = refreshedStrategy;
    (wrapper.instance() as EcsCapacityProvider).componentDidUpdate();

    expect(wrapper.state('capacityProviderStrategy')).toBe(refreshedStrategy);
    expect(command.capacityProviderStrategy).toBe(refreshedStrategy);
    expect(notifyAngular).toHaveBeenCalledOnceWith('capacityProviderStrategy', refreshedStrategy);

    (wrapper.instance() as EcsCapacityProvider).componentDidUpdate();
    expect(notifyAngular).toHaveBeenCalledTimes(1);
  });

  it('gives interactive fields accessible names across all wizard pages', () => {
    const command = buildCommand({
      backingData: {
        accounts: ['test-account'],
        filtered: {
          availableCapacityProviders: ['FARGATE'],
          defaultCapacityProviderStrategy: [],
          ecsClusters: ['test-cluster'],
          iamRoles: ['ecs-role'],
          images: [{ imageId: 'registry/image:latest' }],
          regions: [{ name: 'us-east-1' }],
          secrets: ['docker-secret'],
          securityGroupNames: ['test-sg'],
          subnetTypes: [{ purpose: 'internal', vpcId: 'vpc-1' }],
          targetGroups: ['test-target'],
        },
        launchTypes: ['FARGATE'],
        networkModes: ['awsvpc'],
      } as any,
      capacity: { desired: 1, max: 2, min: 1 },
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }],
      computeOption: 'launchType',
      containerMappings: [{ containerName: 'api', imageDescription: { imageId: 'registry/image:latest' } as any }],
      credentials: 'test-account',
      dockerImageCredentialsSecret: 'docker-secret',
      ecsClusterName: 'test-cluster',
      iamRole: 'ecs-role',
      imageDescription: { imageId: 'registry/image:latest' } as any,
      launchType: 'FARGATE',
      logDriver: 'awslogs',
      networkMode: 'awsvpc',
      placementConstraints: [{ expression: 'attribute:ecs.instance-type =~ t3.*', type: 'memberOf' }],
      region: 'us-east-1',
      serviceDiscoveryAssociations: [
        { containerName: 'api', containerPort: 8080, registry: { displayName: 'registry (registry-1)' } },
      ],
      targetGroupMappings: [{ containerName: 'api', containerPort: 8080, targetGroup: 'test-target' }],
      useDefaultCapacityProviders: false,
      useTaskDefinitionArtifact: true,
    });
    const pageProps = buildProps(command);
    const fieldChange = jasmine.createSpy('onFieldChange');
    const configureCommand = () => Promise.resolve();
    const props = { ...pageProps, configureCommand, onFieldChange: fieldChange };

    const basic = shallow(<BasicSettings {...props} />);
    expect(basic.find(AccountSelectInput).prop('aria-label')).toBe('Account');
    expect(basic.find(RegionSelectInput).prop('aria-label')).toBe('Region');
    expect(findByTestId(basic, 'ServerGroup.clusterName').prop('aria-label')).toBe('ECS Cluster name');
    expect(findByTestId(basic, 'ServerGroup.stack').prop('aria-label')).toBe('Stack');
    expect(findByTestId(basic, 'ServerGroup.details').prop('aria-label')).toBe('Detail');

    const networking = shallow(
      <EcsNetworking command={command} configureCommand={configureCommand} notifyAngular={fieldChange} />,
      { disableLifecycleMethods: true } as any,
    );
    expect(findByTestId(networking, 'Networking.networkMode').find(TetheredSelect).prop('inputProps')).toEqual({
      'aria-label': 'Network mode',
    });
    expect(findByTestId(networking, 'Networking.subnetType').find(TetheredSelect).prop('inputProps')).toEqual({
      'aria-label': 'VPC subnet',
    });
    expect(findByTestId(networking, 'Networking.securityGroups').find(TetheredSelect).prop('inputProps')).toEqual({
      'aria-label': 'Security groups',
    });

    const taskDefinition = shallow(
      <TaskDefinition command={command} configureCommand={configureCommand} notifyAngular={fieldChange} />,
      { disableLifecycleMethods: true } as any,
    );
    expect(findByTestId(taskDefinition, 'Artifacts.containerName').prop('aria-label')).toBe('Container name 1');
    expect(findByTestId(taskDefinition, 'Artifacts.containerImage').find(TetheredSelect).prop('inputProps')).toEqual({
      'aria-label': 'Container image 1',
    });
    expect(findByTestId(taskDefinition, 'Artifacts.targetGroupContainer').prop('aria-label')).toBe(
      'Target group container name 1',
    );
    expect(findByTestId(taskDefinition, 'Artifacts.targetGroup').find(TetheredSelect).prop('inputProps')).toEqual({
      'aria-label': 'Target group 1',
    });
    expect(findByTestId(taskDefinition, 'Artifacts.targetGroupPort').prop('aria-label')).toBe('Target port 1');

    const container = shallow(
      <Container command={command} configureCommand={configureCommand} notifyAngular={fieldChange} />,
      { disableLifecycleMethods: true } as any,
    );
    expect(findByTestId(container, 'ContainerInputs.containerImage').find(TetheredSelect).prop('inputProps')).toEqual({
      'aria-label': 'Container image',
    });
    expect(findByTestId(container, 'ContainerInputs.computeUnits').prop('aria-label')).toBe('Compute units');
    expect(findByTestId(container, 'ContainerInputs.reservedMemory').prop('aria-label')).toBe('Reserved memory');
    expect(findByTestId(container, 'ContainerInputs.targetGroup').find(TetheredSelect).prop('inputProps')).toEqual({
      'aria-label': 'Target group 1',
    });
    expect(findByTestId(container, 'ContainerInputs.targetGroupPort').prop('aria-label')).toBe('Target port 1');

    const scaling = shallow(<HorizontalScalingSettings {...props} />);
    expect(findByTestId(scaling, 'ServerGroup.launchType').prop('aria-label')).toBe('Launch type');
    expect(findByTestId(scaling, 'ServerGroup.capacity.desired').prop('aria-label')).toBe('Desired capacity');
    expect(findByTestId(scaling, 'ServerGroup.capacity.min').prop('aria-label')).toBe('Minimum capacity');
    expect(findByTestId(scaling, 'ServerGroup.capacity.max').prop('aria-label')).toBe('Maximum capacity');
    const capacityProvider = renderCapacityProvider(command);
    expect(findByTestId(capacityProvider, 'ServerGroup.customCapacityProvider.name.0').prop('aria-label')).toBe(
      'Capacity provider name 1',
    );
    expect(findByTestId(capacityProvider, 'ServerGroup.capacityProvider.base.0').prop('aria-label')).toBe(
      'Capacity provider base 1',
    );
    expect(findByTestId(capacityProvider, 'ServerGroup.capacityProvider.weight.0').prop('aria-label')).toBe(
      'Capacity provider weight 1',
    );

    const logging = shallow(<LoggingSettings {...props} />);
    expect(findByTestId(logging, 'Logging.logDriver').prop('aria-label')).toBe('Log driver');
    expect(logging.find(MapEditor).prop('keyLabel')).toBe('Logging option');
    expect(logging.find(MapEditor).prop('valueLabel')).toBe('Logging option value');

    const discovery = shallow(
      <ServiceDiscovery command={command} configureCommand={configureCommand} notifyAngular={fieldChange} />,
      { disableLifecycleMethods: true } as any,
    );
    expect(findByTestId(discovery, 'ServiceDiscovery.containerName').prop('aria-label')).toBe('Container name 1');
    expect(findByTestId(discovery, 'ServiceDiscovery.registry').find(TetheredSelect).prop('inputProps')).toEqual({
      'aria-label': 'Service registry 1',
    });
    expect(findByTestId(discovery, 'ServiceDiscovery.containerPort').prop('aria-label')).toBe('Container port 1');

    const advanced = shallow(
      <AdvancedSettings {...props} command={{ ...command, useTaskDefinitionArtifact: false }} />,
    );
    [
      ['Advanced.healthCheckGracePeriodSeconds', 'Health check grace period'],
      ['Advanced.iamRole', 'ECS IAM instance profile'],
      ['Advanced.dockerImageCredentialsSecret', 'Docker image credentials'],
      ['Advanced.platformVersion', 'Fargate platform version'],
      ['Advanced.enableDeploymentCircuitBreaker', 'Enable deployment circuit breaker'],
      ['Advanced.placementStrategyName', 'Placement strategy'],
      ['Advanced.placementConstraint.type.0', 'Placement constraint type 1'],
      ['Advanced.placementConstraint.expression.0', 'Placement constraint expression 1'],
    ].forEach(([testId, label]) => expect(findByTestId(advanced, testId).prop('aria-label')).toBe(label));
    expect(findByTestId(advanced, 'Advanced.dockerLabels').find(MapEditor).prop('keyLabel')).toBe('Docker label name');
    expect(findByTestId(advanced, 'Advanced.environmentVariables').find(MapEditor).prop('keyLabel')).toBe(
      'Environment variable name',
    );
    expect(findByTestId(advanced, 'Advanced.tags').find(MapEditor).prop('keyLabel')).toBe('Tag name');
  });

  it('restores Advanced Settings service, task, placement, and metadata controls', () => {
    const command = buildCommand({
      backingData: {
        filtered: {
          iamRoles: ['available-role'],
          secrets: ['available-secret'],
        },
      } as any,
      dockerImageCredentialsSecret: 'available-secret',
      dockerLabels: { component: 'api' },
      enableDeploymentCircuitBreaker: true,
      environmentVariables: { ENVIRONMENT: 'production' },
      healthCheckGracePeriodSeconds: 60,
      iamRole: 'available-role',
      placementConstraints: [{ expression: 'attribute:ecs.instance-type =~ t3.*', type: 'memberOf' }],
      placementStrategyName: 'BinPack CPU',
      platformVersion: '1.4.0',
      tags: { owner: 'payments' },
      useTaskDefinitionArtifact: false,
    });
    const onFieldChange = jasmine.createSpy('onFieldChange');
    const page = shallow(
      React.createElement(AdvancedSettings as any, {
        application: buildProps(command).application,
        command,
        onFieldChange,
      }),
    );

    expect(findByTestId(page, 'Advanced.healthCheckGracePeriodSeconds').prop('value')).toBe(60);
    expect(findByTestId(page, 'Advanced.iamRole').prop('value')).toBe('available-role');
    expect(findByTestId(page, 'Advanced.dockerImageCredentialsSecret').prop('value')).toBe('available-secret');
    expect(findByTestId(page, 'Advanced.platformVersion').prop('value')).toBe('1.4.0');
    expect(findByTestId(page, 'Advanced.enableDeploymentCircuitBreaker').prop('checked')).toBe(true);
    expect(findByTestId(page, 'Advanced.placementStrategyName').prop('value')).toBe('BinPack CPU');
    expect(findByTestId(page, 'Advanced.placementConstraint.type.0').prop('value')).toBe('memberOf');
    expect(findByTestId(page, 'Advanced.placementConstraint.expression.0').prop('value')).toContain('t3.*');
    expect(findByTestId(page, 'Advanced.dockerLabels').find(MapEditor).prop('model')).toEqual({ component: 'api' });
    expect(findByTestId(page, 'Advanced.environmentVariables').find(MapEditor).prop('model')).toEqual({
      ENVIRONMENT: 'production',
    });
    expect(findByTestId(page, 'Advanced.tags').find(MapEditor).prop('model')).toEqual({ owner: 'payments' });

    findByTestId(page, 'Advanced.placementStrategyName').simulate('change', { target: { value: 'One Task Per Host' } });
    expect(onFieldChange).toHaveBeenCalledWith('placementStrategyName', 'One Task Per Host');

    const artifactPage = shallow(
      React.createElement(AdvancedSettings as any, {
        application: buildProps(command).application,
        command: { ...command, useTaskDefinitionArtifact: true },
        onFieldChange,
      }),
    );
    expect(findByTestId(artifactPage, 'Advanced.dockerImageCredentialsSecret').exists()).toBe(false);
    expect(findByTestId(artifactPage, 'Advanced.dockerLabels').exists()).toBe(false);
    expect(findByTestId(artifactPage, 'Advanced.environmentVariables').exists()).toBe(false);
    expect(findByTestId(artifactPage, 'Advanced.tags').exists()).toBe(true);
  });

  it('only includes target groups from the selected account', () => {
    const command = buildCommand({ credentials: 'selected-account', region: 'us-east-1' });
    const modal = buildUnrenderedModal(command) as any;
    const loadBalancers = [
      {
        accounts: [
          {
            name: 'selected-account',
            regions: [
              {
                loadBalancers: [
                  {
                    targetGroups: ['selected-target', { targetGroupName: 'selected-object-target' }],
                  },
                ],
                name: 'us-east-1',
              },
            ],
          },
          {
            name: 'other-account',
            regions: [
              {
                loadBalancers: [{ targetGroups: ['other-account-target'] }],
                name: 'us-east-1',
              },
            ],
          },
        ],
      },
    ];

    expect(modal.getTargetGroups(command, loadBalancers)).toEqual(['selected-target', 'selected-object-target']);
  });

  it('only includes target groups from the selected region while preserving deduplicated selections', () => {
    const command = buildCommand({
      credentials: 'selected-account',
      region: 'us-east-1',
      targetGroup: 'legacy-unavailable-target',
      targetGroupMappings: [
        { targetGroup: 'persisted-unavailable-target' } as any,
        { targetGroup: 'duplicate-target' } as any,
      ],
    });
    const modal = buildUnrenderedModal(command) as any;
    const loadBalancers = [
      {
        accounts: [
          {
            name: 'selected-account',
            regions: [
              {
                loadBalancers: [
                  {
                    targetGroups: ['duplicate-target', 'available-target', 'available-target'],
                  },
                ],
                name: 'us-east-1',
              },
              {
                loadBalancers: [{ targetGroups: ['other-region-target'] }],
                name: 'eu-west-1',
              },
            ],
          },
        ],
      },
    ];

    expect(modal.getTargetGroups(command, loadBalancers)).toEqual([
      'duplicate-target',
      'available-target',
      'persisted-unavailable-target',
      'legacy-unavailable-target',
    ]);
  });

  it('starts custom capacity provider mode empty when switching from the cluster default', () => {
    const command = buildCommand({
      backingData: {
        filtered: {
          availableCapacityProviders: ['FARGATE_SPOT'],
          defaultCapacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE_SPOT', weight: 1 }],
        },
      } as any,
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE_SPOT', weight: 1 }],
      credentials: 'ecs-account',
      ecsClusterName: 'cluster',
      region: 'eu-west-1',
      useDefaultCapacityProviders: true,
    });
    const wrapper = renderCapacityProvider(command);

    findByTestId(wrapper, 'ServerGroup.capacityProviders.custom').simulate('click');
    findByTestId(wrapper, 'ServerGroup.addCapacityProvider').simulate('click');

    expect(command.useDefaultCapacityProviders).toBe(false);
    expect(command.capacityProviderStrategy).toEqual([{ base: null, capacityProvider: '', weight: null } as any]);
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
    const wrapper = renderCapacityProvider(command);
    expect(findByTestId(wrapper, 'ServerGroup.capacityProvider.base.1').prop('value')).toBe(1);

    findByTestId(wrapper, 'ServerGroup.capacityProvider.weight.1').simulate('change', {
      target: { valueAsNumber: 3 },
    });

    expect(command.capacityProviderStrategy[1].weight).toBe(3);
  });

  it('renders custom capacity provider names as standard inputs', () => {
    const command = buildCommand({
      capacityProviderMode: 'custom',
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }],
      useDefaultCapacityProviders: false,
    });
    const wrapper = renderCapacityProvider(command);
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
    const wrapper = renderCapacityProvider(command);

    expect(findByTestId(wrapper, 'ServerGroup.addCapacityProvider').exists()).toBe(false);
    expect(findByTestId(wrapper, 'ServerGroup.defaultCapacityProvider.name.0').prop('value')).toBe('FARGATE_SPOT');
  });

  it('does not synthesize FARGATE_SPOT when the cluster has no default capacity provider strategy', () => {
    const command = buildCommand({
      backingData: { ...buildCommand().backingData, filtered: { defaultCapacityProviderStrategy: [] } } as any,
      capacityProviderStrategy: [],
      useDefaultCapacityProviders: false,
    });
    const wrapper = renderCapacityProvider(command);

    findByTestId(wrapper, 'ServerGroup.capacityProviders.default').simulate('click');

    expect(command.capacityProviderStrategy).toEqual([]);
    expect(wrapper.state('capacityProviderStrategy')).toEqual([]);
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
    const wrapper = renderCapacityProvider(command);
    wrapper.setState({ activeCapacityProviderIndex: null });
    expect(wrapper.find('.Select-option').length).toBe(0);

    wrapper.setState({ activeCapacityProviderIndex: 1 });
    expect(wrapper.find('.Select-option').length).toBeGreaterThan(0);
    expect(findByTestId(wrapper, 'ServerGroup.customCapacityProvider.name.1').exists()).toBe(true);
  });
});

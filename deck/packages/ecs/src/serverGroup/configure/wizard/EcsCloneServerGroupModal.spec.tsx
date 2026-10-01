import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import React from 'react';

import { AccountService, DeckRuntimeContext, RequestBuilder, TaskReader } from '@spinnaker/core';

import { EcsCloneServerGroupModalComponent as EcsCloneServerGroupModal } from './EcsCloneServerGroupModal';
import { EcsCapacityProvider } from './capacityProvider/CapacityProvider';
import { Container } from './container/Container';
// ModalContext is not exported from @spinnaker/core.
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import { ModalContext } from '../../../../../core/src/presentation/modal/ModalContext';
import { EcsClusterReader } from '../../../ecsCluster/ecsCluster.read.service';
import { IamRoleReader } from '../../../iamRoles/iamRole.read.service';
import { MetricAlarmReader } from '../../../metricAlarm/metricAlarm.read.service';
import { EcsNetworking } from './networking/Networking';
import { AdvancedSettings } from './pages/AdvancedSettings';
import { BasicSettings } from './pages/BasicSettings';
import { HorizontalScalingSettings } from './pages/HorizontalScalingSettings';
import { LoggingSettings } from './pages/LoggingSettings';
import { TaskDefinitionSettings } from './pages/TaskDefinitionSettings';
import { validateEcsCapacity, validateEcsServerGroup, validateEcsTaskDefinition } from './pages/validation';
import { SecretReader } from '../../../secrets/secret.read.service';
import type { IEcsServerGroupCommand } from '../serverGroupConfiguration.service';
import { ServiceDiscovery } from './serviceDiscovery/ServiceDiscovery';
import { ServiceDiscoveryReader } from '../../../serviceDiscovery/serviceDiscovery.read.service';
import { TaskDefinition } from './taskDefinition/TaskDefinition';

const buildCommand = (overrides: Partial<IEcsServerGroupCommand> = {}): IEcsServerGroupCommand =>
  ({
    backingData: {
      filtered: {
        availableCapacityProviders: [],
        defaultCapacityProviderStrategy: [],
        ecsClusters: [],
        iamRoles: [],
        images: [],
        metricAlarms: [],
        secrets: [],
        securityGroupNames: [],
        serviceDiscoveryRegistries: [],
        subnetTypes: [],
        targetGroups: [],
      },
      launchTypes: ['EC2', 'FARGATE'],
      networkModes: ['bridge', 'awsvpc'],
    },
    capacity: { desired: 1, max: 2, min: 0 },
    capacityProviderStrategy: [],
    computeOption: 'launchType',
    containerMappings: [],
    credentials: 'account-a',
    ecsClusterName: 'cluster-a',
    imageDescription: { imageId: 'registry/api:latest' },
    launchType: 'FARGATE',
    region: 'eu-west-1',
    targetGroupMappings: [],
    taskDefinitionArtifact: {},
    useDefaultCapacityProviders: true,
    viewState: { contextImages: [], dirty: {} },
    ...overrides,
  } as any);

const application = {
  getDataSource: vi.fn().mockReturnValue(null),
  name: 'app',
  serverGroups: { onNextRefresh: vi.fn(), refresh: vi.fn() },
} as any;

const pageProps = (command: IEcsServerGroupCommand, onFieldChange = vi.fn()) => ({
  application,
  command,
  configureCommand: vi.fn().mockResolvedValue(undefined),
  onFieldChange,
});

const renderCapacityProvider = async (command: IEcsServerGroupCommand, onFieldChange = vi.fn()) => {
  const rendered = render(
    <EcsCapacityProvider
      command={command}
      configureCommand={vi.fn().mockResolvedValue(undefined)}
      onFieldChange={onFieldChange}
    />,
  );
  await waitFor(() => expect(screen.queryByText('Loading capacity providers...')).not.toBeInTheDocument());
  return { ...rendered, onFieldChange };
};

const openSelect = (name: string): HTMLElement => {
  const select = screen.getByRole('combobox', { name });
  fireEvent.mouseDown(select);
  return select;
};

const selectOption = async (name: string, option: string): Promise<void> => {
  openSelect(name);
  fireEvent.mouseDown(await screen.findByRole('option', { name: option }));
};

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: any) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

const buildModalProps = (command: IEcsServerGroupCommand, overrides: Record<string, any> = {}) => ({
  application,
  closeModal: vi.fn(),
  command,
  dismissModal: vi.fn(),
  router: {},
  stateParams: {},
  stateService: { go: vi.fn(), includes: vi.fn().mockReturnValue(false) },
  title: 'Deploy ECS server group',
  ...overrides,
});

const renderModal = (
  command = buildCommand(),
  runtimeServices: Record<string, any> = { serverGroupWriter: { cloneServerGroup: vi.fn() } },
  overrides: Record<string, any> = {},
) => {
  const props = buildModalProps(command, overrides);
  const router = new UIRouterReact();
  routers.push(router);
  const rendered = render(
    <UIRouterContext.Provider value={router}>
      <ModalContext.Provider value={{ onRequestClose: vi.fn() }}>
        <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>
          <EcsCloneServerGroupModal {...(props as any)} />
        </DeckRuntimeContext.Provider>
      </ModalContext.Provider>
    </UIRouterContext.Provider>,
  );
  return { ...rendered, props, runtimeServices };
};

const routers: UIRouterReact[] = [];

describe('EcsCloneServerGroupModal', () => {
  let originalHttpClient: typeof RequestBuilder.defaultHttpClient;

  beforeEach(() => {
    originalHttpClient = RequestBuilder.defaultHttpClient;
    vi.spyOn(AccountService, 'getArtifactAccounts').mockResolvedValue([]);
    // TaskDefinition and Container load docker registry accounts on mount.
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([]);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockResolvedValue({
      'account-a': {
        name: 'account-a',
        regions: [
          { availabilityZones: ['eu-west-1a'], name: 'eu-west-1' },
          { availabilityZones: ['us-east-1a'], name: 'us-east-1' },
        ],
      },
      'account-b': {
        name: 'account-b',
        regions: [{ availabilityZones: ['us-west-2a'], name: 'us-west-2' }],
      },
    } as any);
    vi.spyOn(AccountService, 'listAllAccounts').mockResolvedValue([
      { authorized: true, name: 'account-a', regions: [], type: 'ecs' },
      { authorized: true, name: 'account-b', regions: [], type: 'ecs' },
    ] as any);
    vi.spyOn(IamRoleReader.prototype, 'listRoles').mockResolvedValue([]);
    vi.spyOn(EcsClusterReader.prototype, 'listClusters').mockResolvedValue([
      { account: 'account-a', name: 'cluster-a', region: 'eu-west-1' },
      { account: 'account-a', name: 'cluster-us', region: 'us-east-1' },
      { account: 'account-b', name: 'cluster-b', region: 'us-west-2' },
    ] as any);
    vi.spyOn(EcsClusterReader.prototype, 'describeClusters').mockResolvedValue([]);
    vi.spyOn(MetricAlarmReader.prototype, 'listMetricAlarms').mockResolvedValue([]);
    vi.spyOn(SecretReader.prototype, 'listSecrets').mockResolvedValue([]);
    vi.spyOn(ServiceDiscoveryReader, 'listServiceDiscoveryRegistries').mockResolvedValue([]);
    RequestBuilder.defaultHttpClient = {
      get: vi.fn((config: any) => {
        if (config.url.endsWith('securityGroups')) {
          return Promise.resolve({});
        }
        return Promise.resolve([]);
      }),
    } as any;
  });

  afterEach(() => {
    RequestBuilder.defaultHttpClient = originalHttpClient;
    cleanup();
    routers.splice(0).forEach((router) => router.dispose());
  });

  it('does not apply backing data when the mounted modal request resolves after unmount', async () => {
    const loadBalancers = deferred<any[]>();
    const get = vi.fn((config: any) => {
      if (config.url.endsWith('loadBalancers')) {
        return loadBalancers.promise;
      }
      return Promise.resolve(config.url.endsWith('securityGroups') ? {} : []);
    });
    RequestBuilder.defaultHttpClient = { get } as any;
    const command = buildCommand();
    const rendered = renderModal(command);
    await waitFor(() => expect(get).toHaveBeenCalled());

    rendered.unmount();
    await act(async () => loadBalancers.resolve([{ name: 'late-load-balancer' }]));

    expect(command.backingData.loadBalancers).toBeUndefined();
  });

  it('ignores stale mounted-modal backing data requests after a newer Formik location change', async () => {
    renderModal();
    expect(await screen.findByRole('heading', { name: 'Basic Settings' })).toBeInTheDocument();
    const oldRequest = deferred<any[]>();
    const newRequest = deferred<any[]>();
    const describeClusters = vi
      .spyOn(EcsClusterReader.prototype, 'describeClusters')
      .mockImplementation((_account: string, region: string) =>
        region === 'us-east-1' ? oldRequest.promise : newRequest.promise,
      );

    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'us-east-1' } });
    await waitFor(() => expect(describeClusters).toHaveBeenCalledWith('account-a', 'us-east-1'));
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'eu-west-1' } });
    await waitFor(() => expect(describeClusters).toHaveBeenCalledWith('account-a', 'eu-west-1'));
    await act(async () => newRequest.resolve([]));
    await waitFor(() => expect(screen.getByLabelText('Region')).toHaveValue('eu-west-1'));

    await act(async () => oldRequest.resolve([]));

    expect(screen.getByLabelText('Region')).toHaveValue('eu-west-1');
  });

  it('does not apply stale mounted-modal options after a replacement request', async () => {
    renderModal(buildCommand({ networkMode: 'awsvpc' }));
    expect(await screen.findByRole('heading', { name: 'Networking' })).toBeInTheDocument();
    const staleSecurityGroups = deferred<any>();
    let securityGroupRequest = 0;
    const get = vi.fn((config: any) => {
      if (config.url.endsWith('securityGroups')) {
        return ++securityGroupRequest === 1 ? staleSecurityGroups.promise : Promise.resolve({});
      }
      return Promise.resolve([]);
    });
    RequestBuilder.defaultHttpClient = { get } as any;

    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'us-east-1' } });
    await waitFor(() => expect(get).toHaveBeenCalled());
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'eu-west-1' } });
    await waitFor(() => expect(screen.getByLabelText('Region')).toHaveValue('eu-west-1'));
    await act(async () =>
      staleSecurityGroups.resolve({
        'account-a': { ecs: { 'us-east-1': [{ name: 'stale-security-group', vpcId: 'stale-vpc' }] } },
      }),
    );

    expect(screen.getByText('No security groups found in the selected account/region')).toBeInTheDocument();
    expect(screen.queryByText('stale-security-group')).not.toBeInTheDocument();
  });

  it('does not replace Formik edits made while mounted-modal configuration is pending', async () => {
    const command = buildCommand({
      stack: 'original',
      viewState: { contextImages: [], dirty: {}, mode: 'editPipeline' } as any,
    });
    const rendered = renderModal(command);
    expect(await screen.findByRole('heading', { name: 'Basic Settings' })).toBeInTheDocument();
    const request = deferred<any[]>();
    vi.spyOn(EcsClusterReader.prototype, 'describeClusters').mockReturnValue(request.promise);

    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'us-east-1' } });
    fireEvent.change(screen.getByLabelText('Stack'), { target: { value: 'edited' } });
    await act(async () => request.resolve([]));
    await waitFor(() => expect(screen.getByLabelText('Stack')).toHaveValue('edited'));
    fireEvent.click(screen.getByRole('button', { name: 'Done' }));

    await waitFor(() =>
      expect(rendered.props.closeModal).toHaveBeenCalledWith(expect.objectContaining({ stack: 'edited' })),
    );
  });

  it('merges refreshed backing data into Formik edits made while mounted-modal configuration is pending', async () => {
    const command = buildCommand({ stack: 'original' });
    const rendered = renderModal(command);
    expect(await screen.findByRole('heading', { name: 'Basic Settings' })).toBeInTheDocument();
    const request = deferred<any[]>();
    vi.spyOn(EcsClusterReader.prototype, 'describeClusters').mockReturnValue(request.promise);

    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'us-east-1' } });
    fireEvent.change(screen.getByLabelText('Stack'), { target: { value: 'edited' } });
    await act(async () => request.resolve([{ clusterName: 'cluster-a', capacityProviders: ['FARGATE_SPOT'] }]));

    await waitFor(() => expect(screen.getByLabelText('Stack')).toHaveValue('edited'));
    fireEvent.click(screen.getByLabelText('Capacity Providers'));
    await waitFor(() => expect(screen.queryByText('Loading capacity providers...')).not.toBeInTheDocument());
    fireEvent.click(screen.getByLabelText(/Use custom/));
    fireEvent.click(await screen.findByRole('button', { name: 'Add New Capacity Provider' }));
    fireEvent.focus(screen.getByLabelText('Capacity provider name 1'));
    expect(screen.getByRole('button', { name: 'FARGATE_SPOT' })).toBeInTheDocument();
  });

  it('submits mounted-modal subnet changes from the public control', async () => {
    const command = buildCommand({
      networkMode: 'awsvpc',
      subnetTypes: ['private'],
      viewState: { contextImages: [], dirty: {}, mode: 'editPipeline' },
    });
    const get = vi.fn((config: any) => {
      if (config.url.endsWith('subnets/ecs')) {
        return Promise.resolve([
          { account: 'account-a', purpose: 'private', region: 'eu-west-1', vpcId: 'vpc-private' },
          { account: 'account-a', purpose: 'public', region: 'eu-west-1', vpcId: 'vpc-public' },
        ]);
      }
      if (config.url.endsWith('securityGroups')) {
        return Promise.resolve({
          'account-a': {
            ecs: {
              'eu-west-1': [
                { name: 'private-sg', vpcId: 'vpc-private' },
                { name: 'public-sg', vpcId: 'vpc-public' },
              ],
            },
          },
        });
      }
      return Promise.resolve([]);
    });
    RequestBuilder.defaultHttpClient = {
      get,
    } as any;
    const rendered = renderModal(command);
    expect(await screen.findByLabelText('VPC subnet')).toBeInTheDocument();

    await selectOption('VPC subnet', 'public (vpc-public)');
    await waitFor(() => expect(screen.getByText('public (vpc-public)')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: 'Done' }));

    await waitFor(() =>
      expect(rendered.props.closeModal).toHaveBeenCalledWith(
        expect.objectContaining({ subnetTypes: expect.arrayContaining(['public']) }),
      ),
    );
  });

  it('preserves mounted-modal image options while refreshing backing data', async () => {
    const command = buildCommand({
      containerMappings: [{ containerName: 'worker', imageDescription: { imageId: 'registry/api:v2' } } as any],
      imageDescription: { imageId: 'registry/api:v1' } as any,
      taskDefinitionArtifact: { artifactId: 'task-definition' },
      useTaskDefinitionArtifact: true,
    });
    renderModal(command);
    expect(await screen.findByLabelText('Container image 1')).toBeInTheDocument();

    openSelect('Container image 1');
    expect(await screen.findByText('(registry/api:v1)')).toBeInTheDocument();
    expect(screen.getAllByText('(registry/api:v2)').length).toBeGreaterThan(0);
  });

  it('initializes optional backing data arrays used by mounted child sections', async () => {
    const command = buildCommand({ backingData: { filtered: {} } as any });
    renderModal(command);
    expect(await screen.findByRole('heading', { name: 'Basic Settings' })).toBeInTheDocument();
    expect(command.backingData.filtered).toEqual(
      expect.objectContaining({ iamRoles: [], metricAlarms: [], secrets: [], serviceDiscoveryRegistries: [] }),
    );
  });

  it('populates regions and availability zones for the selected account during mounted configuration', async () => {
    const command = buildCommand();
    renderModal(command);
    expect(await screen.findByLabelText('Region')).toHaveValue('eu-west-1');
    expect(command.backingData.filtered.regions.map((region: any) => region.name)).toEqual(['eu-west-1', 'us-east-1']);
    expect(command.availabilityZones).toEqual(['eu-west-1a']);
  });

  it('reconciles invalid location selections through the mounted account handler before reloading', async () => {
    renderModal();
    expect(await screen.findByLabelText('Account')).toHaveValue('account-a');

    fireEvent.change(screen.getByLabelText('Account'), { target: { value: 'account-b' } });

    await waitFor(() => expect(screen.getByLabelText('Region')).toHaveValue(''));
    expect(screen.getByRole('option', { name: 'us-west-2' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'eu-west-1' })).not.toBeInTheDocument();
  });

  it('filters security groups through the mounted command when the subnet type changes', async () => {
    RequestBuilder.defaultHttpClient = {
      get: vi.fn((config: any) => {
        if (config.url.endsWith('subnets/ecs')) {
          return Promise.resolve([{ account: 'account-a', purpose: 'private', region: 'eu-west-1', vpcId: 'vpc-1' }]);
        }
        if (config.url.endsWith('securityGroups')) {
          return Promise.resolve({
            'account-a': { ecs: { 'eu-west-1': [{ name: 'private-sg', vpcId: 'vpc-1' }] } },
          });
        }
        return Promise.resolve([]);
      }),
    } as any;
    renderModal(buildCommand({ networkMode: 'awsvpc', subnetTypes: ['private'] }));
    expect(await screen.findByRole('heading', { name: 'Networking' })).toBeInTheDocument();
    openSelect('Security groups');
    expect(await screen.findByText('private-sg')).toBeInTheDocument();
  });

  it('does not match security groups when backing subnets have no purpose', async () => {
    RequestBuilder.defaultHttpClient = {
      get: vi.fn((config: any) => {
        if (config.url.endsWith('subnets/ecs')) {
          return Promise.resolve([{ account: 'account-a', region: 'eu-west-1', vpcId: 'vpc-1' }]);
        }
        if (config.url.endsWith('securityGroups')) {
          return Promise.resolve({
            'account-a': { ecs: { 'eu-west-1': [{ name: 'purposeless-sg', vpcId: 'vpc-1' }] } },
          });
        }
        return Promise.resolve([]);
      }),
    } as any;
    renderModal(buildCommand({ networkMode: 'awsvpc', subnetTypes: [] }));
    expect(await screen.findByRole('heading', { name: 'Networking' })).toBeInTheDocument();
    expect(screen.getByText('No security groups found in the selected account/region')).toBeInTheDocument();
    expect(screen.queryByText('purposeless-sg')).not.toBeInTheDocument();
  });

  it('reloads mounted backing data when account or region changes', async () => {
    const describeClusters = vi.spyOn(EcsClusterReader.prototype, 'describeClusters');
    renderModal();
    expect(await screen.findByLabelText('Account')).toHaveValue('account-a');
    describeClusters.mockClear();

    fireEvent.change(screen.getByLabelText('Account'), { target: { value: 'account-b' } });
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'us-west-2' } });

    await waitFor(() => expect(describeClusters).toHaveBeenCalledWith('account-b', 'us-west-2'));
  });

  ['createPipeline', 'editPipeline'].forEach((mode) => {
    it(`submits authoritative WizardModal Formik values without executing infrastructure in ${mode} mode`, async () => {
      const command = buildCommand({ viewState: { contextImages: [], dirty: {}, mode } as any });
      const writer = vi.fn();
      const rendered = renderModal(command, { serverGroupWriter: { cloneServerGroup: writer } });
      expect(await screen.findByRole('heading', { name: 'Basic Settings' })).toBeInTheDocument();
      fireEvent.change(screen.getByLabelText('Detail'), { target: { value: 'authoritative-detail' } });
      fireEvent.click(screen.getByRole('button', { name: 'Done' }));

      await waitFor(() =>
        expect(rendered.props.closeModal).toHaveBeenCalledWith(
          expect.objectContaining({ freeFormDetails: 'authoritative-detail' }),
        ),
      );
      expect(writer).not.toHaveBeenCalled();
    });
  });

  it('uses authoritative Formik values for account normalization, display, and submission', async () => {
    const command = buildCommand({
      viewState: { contextImages: [], dirty: {}, mode: 'editPipeline' } as any,
    });
    const rendered = renderModal(command);
    expect(await screen.findByLabelText('Account')).toHaveValue('account-a');
    fireEvent.change(screen.getByLabelText('Account'), { target: { value: 'account-b' } });
    await waitFor(() => expect(screen.getByLabelText('Region')).toHaveValue(''));
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'us-west-2' } });
    await waitFor(() => expect(screen.getByLabelText('Region')).toHaveValue('us-west-2'));
    fireEvent.change(screen.getByLabelText('ECS Cluster name'), { target: { value: 'cluster-b' } });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Done' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Done' }));

    await waitFor(() =>
      expect(rendered.props.closeModal).toHaveBeenCalledWith(
        expect.objectContaining({ credentials: 'account-b', region: 'us-west-2', ecsClusterName: 'cluster-b' }),
      ),
    );
  });

  it('uses authoritative Formik values across artifact and input mode normalization and submission', async () => {
    const command = buildCommand({
      serviceDiscoveryAssociations: [
        { containerName: 'api', containerPort: 8080, registry: { displayName: 'registry', id: 'registry' } } as any,
      ],
      taskDefinitionArtifact: { artifactId: 'task-definition' },
      useTaskDefinitionArtifact: true,
      viewState: { contextImages: [], dirty: {}, mode: 'editPipeline' } as any,
    });
    const rendered = renderModal(command);
    expect(await screen.findByLabelText('Artifact')).toBeChecked();
    fireEvent.click(screen.getByLabelText('Inputs'));
    expect(screen.getByRole('heading', { name: 'Container' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Logging' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Done' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Done' }));
    await waitFor(() => expect(rendered.props.closeModal).toHaveBeenCalled());
    expect(rendered.props.closeModal.mock.lastCall[0].serviceDiscoveryAssociations[0].containerName).toBeNull();

    fireEvent.click(screen.getByLabelText('Artifact'));
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Container' })).not.toBeInTheDocument());
    await waitFor(() => expect(screen.getByLabelText('Container name 1')).toHaveValue(''));
    expect(screen.getByRole('button', { name: 'Done' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Container name 1'), { target: { value: 'worker' } });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Done' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: 'Done' }));
    await waitFor(() => expect(rendered.props.closeModal).toHaveBeenCalledTimes(2));
    expect(rendered.props.closeModal.mock.lastCall[0].serviceDiscoveryAssociations[0].containerName).toBe('worker');
  });

  it('composes sequential WizardModal Formik updates before values settle', async () => {
    const command = buildCommand({ viewState: { contextImages: [], dirty: {}, mode: 'editPipeline' } as any });
    const rendered = renderModal(command);
    expect(await screen.findByLabelText(/use the previous server group's capacity/i)).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText(/use the previous server group's capacity/i));
    fireEvent.click(screen.getByRole('button', { name: 'Done' }));

    await waitFor(() => expect(rendered.props.closeModal).toHaveBeenCalled());
    expect(rendered.props.closeModal.mock.lastCall[0]).toEqual(
      expect.objectContaining({ preferSourceCapacity: true, useSourceCapacity: true }),
    );
  });

  ['create', 'clone'].forEach((mode) => {
    it(`submits ad-hoc ${mode} WizardModal values through TaskMonitor`, async () => {
      const command = buildCommand({ viewState: { contextImages: [], dirty: {}, mode } as any });
      const writer = vi.fn().mockResolvedValue({ id: 'task' });
      vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockReturnValue(new Promise(() => undefined));
      renderModal(command, { serverGroupWriter: { cloneServerGroup: writer } });
      expect(await screen.findByRole('button', { name: 'Done' })).toBeEnabled();

      fireEvent.change(screen.getByLabelText('Detail'), { target: { value: `${mode}-detail` } });
      fireEvent.click(screen.getByRole('button', { name: 'Done' }));

      await waitFor(() =>
        expect(writer).toHaveBeenCalledWith(
          expect.objectContaining({ freeFormDetails: `${mode}-detail` }),
          application,
        ),
      );
    });
  });

  it('refreshes server groups and navigates to the created group through TaskMonitor completion', async () => {
    const task = {
      execution: {
        stages: [{ context: { 'deploy.server.groups': { 'eu-west-1': 'app-main-v042' } }, type: 'cloneServerGroup' }],
      },
      id: 'task',
    } as any;
    const writer = vi.fn().mockResolvedValue(task);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue(task);
    const refreshCallbacks: Array<() => void> = [];
    application.serverGroups.onNextRefresh.mockImplementation((callback: () => void) => {
      refreshCallbacks.push(callback);
      return vi.fn();
    });
    const stateService = {
      go: vi.fn(),
      includes: vi.fn((name: string) => name === '**.clusters'),
    };
    renderModal(buildCommand(), { serverGroupWriter: { cloneServerGroup: writer } }, { stateService });
    expect(await screen.findByRole('button', { name: 'Done' })).toBeEnabled();

    fireEvent.click(screen.getByRole('button', { name: 'Done' }));
    await waitFor(() => expect(application.serverGroups.refresh).toHaveBeenCalled());
    refreshCallbacks[0]();

    expect(stateService.go).toHaveBeenCalledWith('.serverGroup', {
      accountId: 'account-a',
      provider: 'ecs',
      region: 'eu-west-1',
      serverGroup: 'app-main-v042',
    });
  });

  it('owns the TaskMonitor refresh subscription after public submission and unmount', async () => {
    const unsubscribe = vi.fn();
    const callbacks: Array<() => void> = [];
    application.serverGroups.onNextRefresh.mockImplementation((callback: () => void) => {
      callbacks.push(callback);
      return unsubscribe;
    });
    const stateService = { go: vi.fn(), includes: vi.fn() };
    const task = { execution: { stages: [] }, id: 'task' } as any;
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue(task);
    const writer = vi.fn().mockResolvedValue(task);
    const rendered = renderModal(buildCommand(), { serverGroupWriter: { cloneServerGroup: writer } }, { stateService });
    expect(await screen.findByRole('button', { name: 'Done' })).toBeEnabled();

    fireEvent.click(screen.getByRole('button', { name: 'Done' }));
    await waitFor(() => expect(application.serverGroups.refresh).toHaveBeenCalledOnce());
    rendered.unmount();
    callbacks[0]();

    expect(unsubscribe).toHaveBeenCalledOnce();
    expect(stateService.go).not.toHaveBeenCalled();
  });

  it('retains modal command state when ad-hoc submission fails', async () => {
    const command = buildCommand({ viewState: { contextImages: [], dirty: {}, mode: 'create' } as any });
    const writer = vi.fn().mockRejectedValue({ failureMessage: 'create failed' });
    const rendered = renderModal(command, { serverGroupWriter: { cloneServerGroup: writer } });
    expect(await screen.findByRole('button', { name: 'Done' })).toBeEnabled();

    fireEvent.click(screen.getByRole('button', { name: 'Done' }));

    expect(await screen.findByText('create failed')).toBeInTheDocument();
    expect(rendered.props.closeModal).not.toHaveBeenCalled();
    expect(rendered.props.dismissModal).not.toHaveBeenCalled();
  });

  it('renders actual task submission status inside the WizardModal', async () => {
    const task = deferred<any>();
    renderModal(buildCommand(), { serverGroupWriter: { cloneServerGroup: () => task.promise } });
    expect(await screen.findByRole('button', { name: 'Done' })).toBeEnabled();

    fireEvent.click(screen.getByRole('button', { name: 'Done' }));

    expect(await screen.findByText('Creating your server group')).toBeInTheDocument();
  });

  it('renders the legacy eight-page grouping through the actual WizardModal', async () => {
    const command = buildCommand({ useTaskDefinitionArtifact: false });
    renderModal(command);
    expect(await screen.findByRole('heading', { name: 'Basic Settings' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 4 }).map((heading) => heading.textContent)).toEqual([
      'Deploy ECS server group',
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

  it('validates required location, task source, and populated mapping fields through the actual WizardModal', async () => {
    const command = buildCommand({
      credentials: '',
      ecsClusterName: '',
      region: '',
      taskDefinitionArtifact: {},
      useTaskDefinitionArtifact: true,
      containerMappings: [{ containerName: '', imageDescription: {} } as any],
      targetGroupMappings: [{ containerName: '', containerPort: '' as any, targetGroup: '' }],
      serviceDiscoveryAssociations: [
        { containerName: '', containerPort: '', registry: { displayName: '', id: '' } } as any,
      ],
    });
    const rendered = renderModal(command);
    expect(await screen.findByRole('heading', { name: 'Basic Settings' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Done' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: 'Done' }));
    expect(rendered.props.closeModal).not.toHaveBeenCalled();

    const errors = validateEcsServerGroup(command);
    expect(errors).toEqual(
      expect.objectContaining({
        credentials: expect.any(String),
        ecsClusterName: expect.any(String),
        region: expect.any(String),
      }),
    );
    expect(errors.containerMappings[0]).toEqual(
      expect.objectContaining({ containerName: expect.any(String), imageDescription: expect.any(String) }),
    );
    expect(errors.targetGroupMappings[0]).toEqual(
      expect.objectContaining({
        containerName: expect.any(String),
        containerPort: expect.any(String),
        targetGroup: expect.any(String),
      }),
    );
    expect(errors.serviceDiscoveryAssociations[0]).toEqual(
      expect.objectContaining({
        containerName: expect.any(String),
        containerPort: expect.any(String),
        registry: expect.any(String),
      }),
    );
  });

  it('validates naming patterns and finite ordered capacity through production validators', () => {
    const command = buildCommand({
      credentials: 'account',
      ecsClusterName: 'cluster',
      freeFormDetails: 'detail!',
      launchType: 'FARGATE',
      region: 'eu-west-1',
      stack: 'main-stack',
      taskDefinitionArtifact: { artifactId: 'id' },
      useTaskDefinitionArtifact: true,
      capacity: { desired: Infinity, max: 1.5, min: NaN },
    });
    const errors = validateEcsServerGroup(command);
    expect(errors).toEqual(expect.objectContaining({ stack: expect.any(String), freeFormDetails: expect.any(String) }));
    expect(errors.capacity).toEqual(
      expect.objectContaining({ desired: expect.any(String), max: expect.any(String), min: expect.any(String) }),
    );
  });

  it('requires a task definition artifact only when artifact mode is selected', () => {
    const artifactCommand = buildCommand({ taskDefinitionArtifact: {}, useTaskDefinitionArtifact: true });

    expect(validateEcsTaskDefinition(artifactCommand)).toEqual({
      taskDefinitionArtifact: 'Task definition artifact is required.',
    });
    expect(validateEcsTaskDefinition({ ...artifactCommand, useTaskDefinitionArtifact: false })).toEqual({});
  });

  it('accepts a complete task definition artifact command', () => {
    const command = buildCommand({
      containerMappings: [{ containerName: 'api', imageDescription: { imageId: 'registry/api:v1' } } as any],
      targetGroupMappings: [{ containerName: 'api', containerPort: 8080, targetGroup: 'api-target' }],
      taskDefinitionArtifact: { artifactId: 'task-definition' },
      useTaskDefinitionArtifact: true,
    });

    expect(validateEcsTaskDefinition(command)).toEqual({});
    expect(validateEcsServerGroup(command)).toEqual({});
  });

  it.each([
    [
      { min: 3, desired: 3, max: 2 },
      {
        min: 'Minimum capacity cannot exceed maximum capacity.',
        desired: 'Desired capacity must be between minimum and maximum capacity.',
      },
    ],
    [{ min: 1, desired: 0, max: 3 }, { desired: 'Desired capacity must be between minimum and maximum capacity.' }],
    [{ min: 1, desired: 4, max: 3 }, { desired: 'Desired capacity must be between minimum and maximum capacity.' }],
  ])('rejects unordered capacity %o with the exact relationship error', (capacity, expected) => {
    expect(validateEcsCapacity(buildCommand({ capacity })).capacity).toEqual(expected);
  });

  it('accepts capacity ordered as minimum, desired, maximum', () => {
    expect(validateEcsCapacity(buildCommand({ capacity: { min: 1, desired: 2, max: 3 } })).capacity).toBeUndefined();
  });

  it('requires a launch type in launch type compute mode', () => {
    const command = buildCommand({ computeOption: 'launchType', launchType: '' });
    expect(validateEcsCapacity(command).launchType).toBeTruthy();
    expect(validateEcsCapacity({ ...command, launchType: 'FARGATE' }).launchType).toBeUndefined();
  });

  it('requires a usable default strategy in default capacity provider mode', () => {
    const command = buildCommand({ computeOption: 'capacityProviders', useDefaultCapacityProviders: true });
    expect(validateEcsCapacity(command).capacityProviderStrategy).toBeTruthy();
    command.backingData.filtered.defaultCapacityProviderStrategy = [
      { base: 0, capacityProvider: 'FARGATE', weight: 1 },
    ];
    expect(validateEcsCapacity(command).capacityProviderStrategy).toBeUndefined();
  });

  it('validates every custom capacity provider strategy row', () => {
    const command = buildCommand({
      computeOption: 'capacityProviders',
      useDefaultCapacityProviders: false,
      capacityProviderStrategy: [
        { base: -1, capacityProvider: '', weight: Infinity },
        { base: 1.5, capacityProvider: 'FARGATE', weight: NaN },
      ],
    });
    const errors = validateEcsCapacity(command).capacityProviderStrategy;
    expect(errors[0]).toEqual(
      expect.objectContaining({
        base: expect.any(String),
        capacityProvider: expect.any(String),
        weight: expect.any(String),
      }),
    );
    expect(errors[1]).toEqual(expect.objectContaining({ base: expect.any(String), weight: expect.any(String) }));
  });

  it('does not submit the wizard from add, remove, or option buttons', async () => {
    const command = buildCommand({
      backingData: {
        ...buildCommand().backingData,
        filtered: {
          ...buildCommand().backingData.filtered,
          availableCapacityProviders: ['FARGATE'],
          serviceDiscoveryRegistries: [{ displayName: 'registry', id: 'id' }],
          targetGroups: ['target'],
        },
      } as any,
      computeOption: 'capacityProviders',
      credentials: 'account',
      ecsClusterName: 'cluster',
      region: 'eu',
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }],
      useDefaultCapacityProviders: false,
    });
    await renderCapacityProvider(command);
    expect(screen.getAllByRole('button').length).toBeGreaterThan(0);
    screen.getAllByRole('button').forEach((button) => expect(button).toHaveAttribute('type', 'button'));
  });

  it('restores Basic Settings account, region, cluster, naming, and strategy controls', async () => {
    const onFieldChange = vi.fn();
    const command = buildCommand({
      backingData: {
        accounts: ['account-a', 'account-b'],
        filtered: { ecsClusters: ['cluster'], regions: [{ name: 'eu-west-1' }] },
      } as any,
      credentials: 'account-a',
      ecsClusterName: 'cluster',
      freeFormDetails: 'api',
      region: 'eu-west-1',
      selectedProvider: 'ecs',
      stack: 'prod',
    });
    const rendered = render(<BasicSettings {...pageProps(command, onFieldChange)} />);
    await waitFor(() => expect(screen.getByLabelText('Account')).toHaveValue('account-a'));
    expect(screen.getByLabelText('Region')).toHaveValue('eu-west-1');
    expect(screen.getByLabelText('ECS Cluster name')).toHaveValue('cluster');
    expect(screen.getByLabelText('Stack')).toHaveValue('prod');
    expect(screen.getByLabelText('Detail')).toHaveValue('api');

    fireEvent.change(screen.getByLabelText('Account'), { target: { value: 'account-b' } });
    fireEvent.change(screen.getByLabelText('Region'), { target: { value: 'eu-west-1' } });
    fireEvent.change(screen.getByLabelText('ECS Cluster name'), { target: { value: 'cluster' } });
    fireEvent.change(screen.getByLabelText('Stack'), { target: { value: 'next' } });
    fireEvent.change(screen.getByLabelText('Detail'), { target: { value: 'worker' } });
    const strategyControl = rendered.container.querySelector('.Select-control') as HTMLElement;
    expect(strategyControl).toBeInTheDocument();
    fireEvent.mouseDown(strategyControl);
    fireEvent.mouseDown(await screen.findByRole('option', { name: 'Highlander' }));

    expect(onFieldChange).toHaveBeenCalledWith('credentials', 'account-b');
    expect(onFieldChange).toHaveBeenCalledWith('region', 'eu-west-1');
    expect(onFieldChange).toHaveBeenCalledWith('ecsClusterName', 'cluster');
    expect(onFieldChange).toHaveBeenCalledWith('stack', 'next');
    expect(onFieldChange).toHaveBeenCalledWith('freeFormDetails', 'worker');
    expect(onFieldChange).toHaveBeenCalledWith('strategy', 'highlander');
  });

  it('restores Networking VPC subnet and security-group controls without hiding persisted references', () => {
    const command = buildCommand({
      backingData: {
        filtered: {
          securityGroupNames: ['available-sg'],
          subnetTypes: [{ purpose: 'available-subnet', vpcId: 'vpc-1' }],
        },
        networkModes: ['awsvpc'],
      } as any,
      networkMode: 'awsvpc',
      securityGroupNames: ['persisted-sg'],
      subnetTypes: ['persisted-subnet'],
    });
    render(<EcsNetworking {...pageProps(command)} />);
    expect(screen.getByLabelText('Network mode')).toBeInTheDocument();
    expect(screen.getByLabelText('VPC subnet')).toBeInTheDocument();
    expect(screen.getByLabelText('Security groups')).toBeInTheDocument();
    expect(screen.getByText('persisted-subnet (unavailable)')).toBeInTheDocument();
    expect(screen.getByText('persisted-sg')).toBeInTheDocument();
  });

  it('restores the actual Task Definition source, artifact mappings, and persisted target groups', async () => {
    const onFieldChange = vi.fn();
    const command = buildCommand({
      backingData: {
        filtered: { images: [{ imageId: 'registry/api:latest' }], targetGroups: ['available-target'] },
      } as any,
      containerMappings: [{ containerName: 'api', imageDescription: { imageId: 'registry/api:latest' } } as any],
      targetGroupMappings: [{ containerName: 'api', containerPort: 8080, targetGroup: 'persisted-target' }],
      taskDefinitionArtifact: { artifactId: 'task-definition' },
      useTaskDefinitionArtifact: true,
    });
    render(<TaskDefinitionSettings {...pageProps(command, onFieldChange)} />);
    expect(screen.getByLabelText('Artifact')).toBeChecked();
    expect(screen.getByLabelText('Container name 1')).toHaveValue('api');
    expect(screen.getByLabelText('Container image 1')).toBeInTheDocument();
    expect(screen.getByLabelText('Target group container name 1')).toHaveValue('api');
    expect(screen.getByLabelText('Target group 1')).toBeInTheDocument();
    expect(screen.getByLabelText('Target port 1')).toHaveValue(8080);
    expect(await screen.findByText('persisted-target')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Container name 1'), { target: { value: 'worker' } });
    fireEvent.change(screen.getByLabelText('Target port 1'), { target: { value: '9090' } });
    expect(onFieldChange).toHaveBeenCalledWith(
      'containerMappings',
      expect.arrayContaining([expect.objectContaining({ containerName: 'worker' })]),
    );
    expect(onFieldChange).toHaveBeenCalledWith(
      'targetGroupMappings',
      expect.arrayContaining([expect.objectContaining({ containerPort: 9090 })]),
    );
  });

  it('restores Container image, resources, and persisted target-group mappings', () => {
    const onFieldChange = vi.fn();
    const command = buildCommand({
      backingData: {
        filtered: { images: [{ imageId: 'registry/api:latest' }], targetGroups: ['available-target'] },
      } as any,
      computeUnits: 512,
      imageDescription: { imageId: 'registry/api:latest' } as any,
      reservedMemory: 1024,
      targetGroupMappings: [{ containerName: '', containerPort: 8080, targetGroup: 'persisted-target' }],
    });
    render(<Container {...pageProps(command, onFieldChange)} />);
    expect(screen.getByLabelText('Container image')).toBeInTheDocument();
    expect(screen.getByLabelText('Compute units')).toHaveValue(512);
    expect(screen.getByLabelText('Reserved memory')).toHaveValue(1024);
    expect(screen.getByLabelText('Target group 1')).toBeInTheDocument();
    expect(screen.getByLabelText('Target port 1')).toHaveValue(8080);
    expect(screen.getByText('persisted-target')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Compute units'), { target: { value: '256' } });
    fireEvent.change(screen.getByLabelText('Reserved memory'), { target: { value: '512' } });
    fireEvent.change(screen.getByLabelText('Target port 1'), { target: { value: '9090' } });
    expect(onFieldChange).toHaveBeenCalledWith('computeUnits', 256);
    expect(onFieldChange).toHaveBeenCalledWith('reservedMemory', 512);
    expect(onFieldChange).toHaveBeenCalledWith(
      'targetGroupMappings',
      expect.arrayContaining([expect.objectContaining({ containerPort: 9090 })]),
    );
  });

  it('restores Horizontal Scaling compute mode, capacity, and source-policy controls', () => {
    const onFieldChange = vi.fn();
    const command = buildCommand({
      capacity: { desired: 3, max: 5, min: 2 },
      computeOption: 'launchType',
      launchType: 'FARGATE',
      copySourceScalingPoliciesAndActions: true,
      copySourceMonitoringConfiguration: true,
    });
    render(<HorizontalScalingSettings {...pageProps(command, onFieldChange)} />);
    expect(screen.getByRole('radio', { name: 'Launch type' })).toBeChecked();
    expect(screen.getByRole('combobox', { name: 'Launch type' })).toHaveValue('FARGATE');
    expect(screen.getByLabelText('Desired capacity')).toHaveValue(3);
    expect(screen.getByLabelText('Minimum capacity')).toHaveValue(2);
    expect(screen.getByLabelText('Maximum capacity')).toHaveValue(5);
    expect(screen.getByLabelText(/previous server group's capacity/)).not.toBeChecked();
    expect(screen.getByLabelText(/previous server group's autoscaling policies/)).toBeChecked();
    expect(screen.getByLabelText(/previous server group's monitoring configuration/)).toBeChecked();
    fireEvent.change(screen.getByLabelText('Minimum capacity'), { target: { value: '1' } });
    expect(onFieldChange).toHaveBeenCalledWith('capacity', { desired: 3, max: 5, min: 1 });
    fireEvent.click(screen.getByLabelText(/previous server group's capacity/));
    expect(onFieldChange).toHaveBeenCalledWith('useSourceCapacity', true);
    expect(onFieldChange).toHaveBeenCalledWith('preferSourceCapacity', true);
    fireEvent.click(screen.getByLabelText(/Capacity Providers/));
    expect(onFieldChange).toHaveBeenCalledWith('computeOption', 'capacityProviders');
    expect(onFieldChange).toHaveBeenCalledWith('launchType', '');
  });

  it('restores Horizontal Scaling monitoring controls and toggles high-resolution metrics', () => {
    const renderPage = (command: IEcsServerGroupCommand, onFieldChange = vi.fn()) =>
      render(<HorizontalScalingSettings {...pageProps(command, onFieldChange)} />);
    const copyMonitoring = () => screen.getByLabelText(/previous server group's monitoring configuration/);
    const metric = (name: string) => screen.getByRole('checkbox', { name });

    const defaultOnFieldChange = vi.fn();
    const defaultPage = renderPage(buildCommand({ copySourceMonitoringConfiguration: true }), defaultOnFieldChange);
    expect(copyMonitoring()).toBeChecked();
    expect(metric('CPUUtilization')).not.toBeChecked();
    expect(metric('MemoryUtilization')).not.toBeChecked();

    fireEvent.click(copyMonitoring());
    expect(defaultOnFieldChange).toHaveBeenCalledWith('copySourceMonitoringConfiguration', false);

    fireEvent.click(metric('CPUUtilization'));
    expect(defaultOnFieldChange).toHaveBeenCalledWith('monitoringConfiguration', {
      metricConfigurations: [{ metricNames: ['CPUUtilization'], resolutionSeconds: 20 }],
    });
    defaultPage.unmount();

    const cpuOnFieldChange = vi.fn();
    const cpuPage = renderPage(
      buildCommand({
        monitoringConfiguration: { metricConfigurations: [{ metricNames: ['CPUUtilization'], resolutionSeconds: 20 }] },
      }),
      cpuOnFieldChange,
    );
    expect(metric('CPUUtilization')).toBeChecked();
    expect(metric('MemoryUtilization')).not.toBeChecked();

    fireEvent.click(metric('MemoryUtilization'));
    expect(cpuOnFieldChange).toHaveBeenCalledWith('monitoringConfiguration', {
      metricConfigurations: [{ metricNames: ['CPUUtilization', 'MemoryUtilization'], resolutionSeconds: 20 }],
    });

    fireEvent.click(metric('CPUUtilization'));
    expect(cpuOnFieldChange).toHaveBeenCalledWith('monitoringConfiguration', undefined);
    cpuPage.unmount();

    renderPage(
      buildCommand({
        monitoringConfiguration: {
          metricConfigurations: [{ metricNames: ['CPUUtilization', 'MemoryUtilization'], resolutionSeconds: 60 }],
        },
      }),
    );
    expect(metric('CPUUtilization')).not.toBeChecked();
  });

  it('restores Logging driver and option-map controls', () => {
    const onFieldChange = vi.fn();
    render(
      <LoggingSettings
        {...pageProps(buildCommand({ logDriver: 'awslogs', logOptions: { region: 'eu' } }), onFieldChange)}
      />,
    );
    expect(screen.getByLabelText('Log driver')).toHaveValue('awslogs');
    expect(screen.getByLabelText('Logging option')).toHaveValue('region');
    expect(screen.getByLabelText('Logging option value')).toHaveValue('eu');
    fireEvent.change(screen.getByLabelText('Logging option value'), { target: { value: 'us' } });
    expect(onFieldChange).toHaveBeenCalledWith('logOptions', { region: 'us' });
    fireEvent.change(screen.getByLabelText('Log driver'), { target: { value: 'fluentd' } });
    expect(onFieldChange).toHaveBeenCalledWith('logDriver', 'fluentd');
  });

  it('restores Service Discovery mappings without hiding persisted registries', () => {
    const onFieldChange = vi.fn();
    const available = { displayName: 'available', id: 'one' } as any;
    const persisted = { displayName: 'persisted', id: 'two' } as any;
    const command = buildCommand({
      backingData: { filtered: { serviceDiscoveryRegistries: [available] } } as any,
      serviceDiscoveryAssociations: [{ containerName: 'api', containerPort: 8080, registry: persisted } as any],
      useTaskDefinitionArtifact: true,
    });
    render(<ServiceDiscovery {...pageProps(command, onFieldChange)} />);
    expect(screen.getByLabelText('Container name 1')).toHaveValue('api');
    expect(screen.getByLabelText('Service registry 1')).toBeInTheDocument();
    expect(screen.getByLabelText('Container port 1')).toHaveValue(8080);
    expect(screen.getByText('persisted')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Container name 1'), { target: { value: 'worker' } });
    fireEvent.change(screen.getByLabelText('Container port 1'), { target: { value: '9090' } });
    expect(onFieldChange).toHaveBeenCalledWith(
      'serviceDiscoveryAssociations',
      expect.arrayContaining([expect.objectContaining({ containerName: 'worker', containerPort: 9090 })]),
    );
  });

  it('refreshes Networking options through the actual component without clobbering selections', async () => {
    const command = buildCommand({
      backingData: { filtered: { securityGroupNames: ['old-sg'], subnetTypes: [] }, networkModes: ['awsvpc'] } as any,
      networkMode: 'awsvpc',
      securityGroupNames: ['persisted-sg'],
      subnetTypes: ['persisted-subnet'],
    });
    const next = {
      ...command,
      backingData: {
        ...command.backingData,
        filtered: {
          ...command.backingData.filtered,
          securityGroupNames: ['new-sg'],
          subnetTypes: [{ purpose: 'new-subnet', vpcId: 'new' }],
        },
      },
    } as any;
    const configureCommand = vi.fn().mockResolvedValue(undefined);
    const rendered = render(
      <EcsNetworking command={command} configureCommand={configureCommand} onFieldChange={vi.fn()} />,
    );
    rendered.rerender(<EcsNetworking command={next} configureCommand={configureCommand} onFieldChange={vi.fn()} />);
    await waitFor(() => expect(configureCommand).toHaveBeenCalledOnce());
    expect(screen.getByText('persisted-sg')).toBeInTheDocument();
    expect(screen.getByText('persisted-subnet (unavailable)')).toBeInTheDocument();
    openSelect('Security groups');
    expect(await screen.findByRole('option', { name: 'new-sg' })).toBeInTheDocument();
    expect(screen.getAllByRole('option', { name: 'persisted-sg' }).length).toBeGreaterThan(0);
    openSelect('VPC subnet');
    expect(await screen.findByRole('option', { name: 'new-subnet (new)' })).toBeInTheDocument();
  });

  it('does not reapply a legacy subnet type during prop reconciliation', async () => {
    const command = buildCommand({
      backingData: {
        filtered: { securityGroupNames: [], subnetTypes: [{ purpose: 'private', vpcId: 'vpc-private' }] },
        networkModes: ['awsvpc'],
      } as any,
      networkMode: 'awsvpc',
      subnetType: 'legacy',
      subnetTypes: ['private'],
    });
    const rendered = render(<EcsNetworking {...pageProps(command)} />);
    expect(screen.getByText('legacy (unavailable)')).toBeInTheDocument();

    const next = {
      ...command,
      backingData: {
        ...command.backingData,
        filtered: {
          ...command.backingData.filtered,
          subnetTypes: [{ purpose: 'public', vpcId: 'vpc-public' }],
        },
      },
      subnetType: 'legacy',
      subnetTypes: ['public'],
    } as any;
    rendered.rerender(<EcsNetworking {...pageProps(next)} />);

    await waitFor(() => expect(screen.getByText('public (vpc-public)')).toBeInTheDocument());
    expect(screen.queryByText('legacy (unavailable)')).not.toBeInTheDocument();
  });

  it('refreshes Container options through the actual component without clobbering selections', async () => {
    const image = { imageId: 'persisted' } as any;
    const command = buildCommand({
      imageDescription: image,
      targetGroupMappings: [{ containerName: '', containerPort: 80, targetGroup: 'persisted' }],
    });
    const next = {
      ...command,
      backingData: {
        ...command.backingData,
        filtered: { ...command.backingData.filtered, images: [{ imageId: 'new' }], targetGroups: ['new'] },
      },
    } as any;
    const onFieldChange = vi.fn();
    const rendered = render(<Container {...pageProps(command, onFieldChange)} />);
    rendered.rerender(<Container {...pageProps(next, onFieldChange)} />);
    expect(screen.getByLabelText('Container image')).toBeInTheDocument();
    expect(screen.getByLabelText('Target group 1')).toBeInTheDocument();
    expect(screen.getByText('persisted')).toBeInTheDocument();
    openSelect('Target group 1');
    expect(await screen.findByRole('option', { name: 'new' })).toBeInTheDocument();
    expect(onFieldChange).not.toHaveBeenCalled();
  });

  it('refreshes TaskDefinition options through the actual component without clobbering selections', async () => {
    const artifact = { artifactId: 'expected' };
    const command = buildCommand({
      taskDefinitionArtifact: artifact,
      targetGroupMappings: [{ containerName: 'api', containerPort: 80, targetGroup: 'persisted' }],
    });
    const next = {
      ...command,
      backingData: {
        ...command.backingData,
        filtered: { ...command.backingData.filtered, images: [{ imageId: 'new' }], targetGroups: ['new'] },
      },
    } as any;
    const onFieldChange = vi.fn();
    const rendered = render(<TaskDefinition {...pageProps(command, onFieldChange)} />);
    rendered.rerender(<TaskDefinition {...pageProps(next, onFieldChange)} />);
    expect(screen.getByLabelText('Target group 1')).toBeInTheDocument();
    expect(screen.getByText('persisted')).toBeInTheDocument();
    openSelect('Target group 1');
    expect(await screen.findByRole('option', { name: 'new' })).toBeInTheDocument();
    expect(onFieldChange).not.toHaveBeenCalled();
  });

  it('refreshes ServiceDiscovery options through the actual component without clobbering selections', async () => {
    const persisted = { displayName: 'persisted', id: 'one' } as any;
    const associations = [{ containerName: 'api', containerPort: 80, registry: persisted }] as any;
    const command = buildCommand({ serviceDiscoveryAssociations: associations, useTaskDefinitionArtifact: true });
    const onFieldChange = vi.fn();
    const rendered = render(<ServiceDiscovery {...pageProps(command, onFieldChange)} />);
    const next = {
      ...command,
      backingData: {
        ...command.backingData,
        filtered: {
          ...command.backingData.filtered,
          serviceDiscoveryRegistries: [{ displayName: 'new', id: 'two' }],
        },
      },
    } as any;
    rendered.rerender(<ServiceDiscovery {...pageProps(next, onFieldChange)} />);
    expect(screen.getByLabelText('Service registry 1')).toBeInTheDocument();
    expect(screen.getByText('persisted')).toBeInTheDocument();
    openSelect('Service registry 1');
    expect(await screen.findByRole('option', { name: 'new' })).toBeInTheDocument();
    expect(onFieldChange).not.toHaveBeenCalled();
  });

  it('normalizes ServiceDiscovery container names through actual prop reconciliation when switching to inputs', async () => {
    const registry = { displayName: 'registry', id: 'registry' } as any;
    const command = buildCommand({
      serviceDiscoveryAssociations: [{ containerName: 'api', containerPort: 80, registry } as any],
      useTaskDefinitionArtifact: true,
    });
    const onFieldChange = vi.fn();
    const rendered = render(<ServiceDiscovery {...pageProps(command, onFieldChange)} />);
    const next = { ...command, useTaskDefinitionArtifact: false };
    rendered.rerender(<ServiceDiscovery {...pageProps(next, onFieldChange)} />);

    expect(screen.queryByLabelText('Container name 1')).not.toBeInTheDocument();
    await waitFor(() =>
      expect(onFieldChange).toHaveBeenCalledWith(
        'serviceDiscoveryAssociations',
        expect.arrayContaining([expect.objectContaining({ containerName: null })]),
      ),
    );
  });

  it('preserves and normalizes ServiceDiscovery container names through actual prop reconciliation for artifacts', async () => {
    const registry = { displayName: 'registry', id: 'registry' } as any;
    const command = buildCommand({
      serviceDiscoveryAssociations: [{ containerName: null, containerPort: 80, registry } as any],
      useTaskDefinitionArtifact: false,
    });
    const onFieldChange = vi.fn();
    const rendered = render(<ServiceDiscovery {...pageProps(command, onFieldChange)} />);
    const next = {
      ...command,
      serviceDiscoveryAssociations: [
        { containerName: null, containerPort: 80, registry },
        { containerName: 'worker', containerPort: 90, registry },
      ],
      useTaskDefinitionArtifact: true,
    } as any;
    rendered.rerender(<ServiceDiscovery {...pageProps(next, onFieldChange)} />);

    await waitFor(() => expect(screen.getByLabelText('Container name 1')).toHaveValue(''));
    expect(screen.getByLabelText('Container name 2')).toHaveValue('worker');
    expect(onFieldChange).toHaveBeenCalledWith(
      'serviceDiscoveryAssociations',
      expect.arrayContaining([
        expect.objectContaining({ containerName: '' }),
        expect.objectContaining({ containerName: 'worker' }),
      ]),
    );
  });

  it('refreshes CapacityProvider options through actual prop reconciliation without clobbering selections', async () => {
    const strategy = [{ base: 0, capacityProvider: 'persisted', weight: 1 }];
    const command = buildCommand({ capacityProviderStrategy: strategy, useDefaultCapacityProviders: false });
    const rendered = await renderCapacityProvider(command);
    const next = {
      ...command,
      backingData: {
        ...command.backingData,
        filtered: { ...command.backingData.filtered, availableCapacityProviders: ['new'] },
      },
      ecsClusterName: 'new-cluster',
    } as any;
    rendered.rerender(
      <EcsCapacityProvider
        command={next}
        configureCommand={vi.fn().mockResolvedValue(undefined)}
        onFieldChange={rendered.onFieldChange}
      />,
    );
    await waitFor(() => expect(screen.getByText('(new-cluster)')).toBeInTheDocument());
    expect(screen.getByLabelText('Capacity provider name 1')).toHaveValue('persisted');
    fireEvent.focus(screen.getByLabelText('Capacity provider name 1'));
    expect(screen.getByRole('button', { name: 'new' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'persisted' })).toBeInTheDocument();
  });

  it('publishes the refreshed cluster strategy through the field callback', async () => {
    const refreshed = [{ base: 1, capacityProvider: 'FARGATE_SPOT', weight: 2 }];
    const command = buildCommand({ useDefaultCapacityProviders: true });
    const onFieldChange = vi.fn();
    render(
      <EcsCapacityProvider
        command={command}
        configureCommand={vi.fn().mockImplementation(async () => {
          command.backingData.filtered.defaultCapacityProviderStrategy = refreshed;
        })}
        onFieldChange={onFieldChange}
      />,
    );

    await waitFor(() => expect(onFieldChange).toHaveBeenCalledWith('capacityProviderStrategy', refreshed));
    expect(screen.getByLabelText('Capacity provider name 1')).toHaveValue('FARGATE_SPOT');
  });

  it('publishes refreshed default strategy props once through the actual component without a notification loop', async () => {
    const original = [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }];
    const refreshed = [{ base: 1, capacityProvider: 'FARGATE_SPOT', weight: 2 }];
    const command = buildCommand({ capacityProviderStrategy: original, useDefaultCapacityProviders: true });
    command.backingData.filtered.defaultCapacityProviderStrategy = original;
    const rendered = await renderCapacityProvider(command);
    rendered.onFieldChange.mockClear();
    const next = {
      ...command,
      backingData: {
        ...command.backingData,
        filtered: { ...command.backingData.filtered, defaultCapacityProviderStrategy: refreshed },
      },
    } as any;
    const component = (
      <EcsCapacityProvider
        command={next}
        configureCommand={vi.fn().mockResolvedValue(undefined)}
        onFieldChange={rendered.onFieldChange}
      />
    );
    rendered.rerender(component);
    await waitFor(() =>
      expect(rendered.onFieldChange).toHaveBeenCalledExactlyOnceWith('capacityProviderStrategy', refreshed),
    );
    rendered.rerender(component);
    expect(rendered.onFieldChange).toHaveBeenCalledTimes(1);
  });

  it('gives interactive fields accessible names across every actual wizard page', async () => {
    const options = {
      ...buildCommand().backingData,
      accounts: ['account-a'],
      filtered: {
        ...buildCommand().backingData.filtered,
        availableCapacityProviders: ['FARGATE'],
        ecsClusters: ['cluster-a'],
        iamRoles: ['role'],
        images: [{ imageId: 'image' }],
        regions: [{ name: 'eu-west-1' }],
        secrets: ['secret'],
        securityGroupNames: ['sg'],
        serviceDiscoveryRegistries: [{ displayName: 'registry', id: 'registry' }],
        subnetTypes: [{ purpose: 'subnet', vpcId: 'vpc' }],
        targetGroups: ['target'],
      },
    } as any;
    const taskCommand = buildCommand({
      backingData: options,
      containerMappings: [{ containerName: 'api', imageDescription: { imageId: 'image' } } as any],
      serviceDiscoveryAssociations: [
        { containerName: 'api', containerPort: 80, registry: { displayName: 'registry', id: 'registry' } } as any,
      ],
      targetGroupMappings: [{ containerName: 'api', containerPort: 80, targetGroup: 'target' }],
      taskDefinitionArtifact: { artifactId: 'task-definition' },
      useTaskDefinitionArtifact: true,
    });
    const containerCommand = buildCommand({
      backingData: options,
      computeUnits: 512,
      reservedMemory: 1024,
      targetGroupMappings: [{ containerName: '', containerPort: 80, targetGroup: 'target' }],
    });
    const capacityCommand = buildCommand({
      backingData: options,
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }],
      computeOption: 'capacityProviders',
      useDefaultCapacityProviders: false,
    });
    const rendered = render(
      <>
        <div data-testid="basic">
          <BasicSettings {...pageProps(buildCommand({ backingData: options }))} />
        </div>
        <div data-testid="networking">
          <EcsNetworking
            {...pageProps(
              buildCommand({
                backingData: options,
                networkMode: 'awsvpc',
                securityGroupNames: ['sg'],
                subnetTypes: ['subnet'],
              }),
            )}
          />
        </div>
        <div data-testid="task-definition">
          <TaskDefinition {...pageProps(taskCommand)} />
        </div>
        <div data-testid="container">
          <Container {...pageProps(containerCommand)} />
        </div>
        <div data-testid="scaling">
          <HorizontalScalingSettings {...pageProps(buildCommand({ backingData: options }))} />
        </div>
        <div data-testid="capacity-provider">
          <EcsCapacityProvider
            command={capacityCommand}
            configureCommand={vi.fn().mockResolvedValue(undefined)}
            onFieldChange={vi.fn()}
          />
        </div>
        <div data-testid="logging">
          <LoggingSettings {...pageProps(buildCommand({ logDriver: 'awslogs' }))} />
        </div>
        <div data-testid="service-discovery">
          <ServiceDiscovery {...pageProps(taskCommand)} />
        </div>
        <div data-testid="advanced">
          <AdvancedSettings
            {...pageProps(
              buildCommand({
                backingData: options,
                placementConstraints: [{ expression: '', type: 'memberOf' }],
                useTaskDefinitionArtifact: false,
              }),
            )}
          />
        </div>
      </>,
    );
    await waitFor(() => expect(within(rendered.getByTestId('basic')).getByLabelText('Account')).toBeInTheDocument());
    ['Account', 'Region', 'ECS Cluster name', 'Stack', 'Detail'].forEach((name) =>
      expect(within(rendered.getByTestId('basic')).getByLabelText(name)).toBeInTheDocument(),
    );
    ['Network mode', 'VPC subnet', 'Security groups'].forEach((name) =>
      expect(within(rendered.getByTestId('networking')).getByLabelText(name)).toBeInTheDocument(),
    );
    [
      'Container name 1',
      'Container image 1',
      'Target group container name 1',
      'Target group 1',
      'Target port 1',
    ].forEach((name) =>
      expect(within(rendered.getByTestId('task-definition')).getByLabelText(name)).toBeInTheDocument(),
    );
    ['Container image', 'Compute units', 'Reserved memory', 'Target group 1', 'Target port 1'].forEach((name) =>
      expect(within(rendered.getByTestId('container')).getByLabelText(name)).toBeInTheDocument(),
    );
    expect(within(rendered.getByTestId('scaling')).getByRole('combobox', { name: 'Launch type' })).toBeInTheDocument();
    ['Desired capacity', 'Minimum capacity', 'Maximum capacity'].forEach((name) =>
      expect(within(rendered.getByTestId('scaling')).getByLabelText(name)).toBeInTheDocument(),
    );
    await waitFor(() =>
      expect(
        within(rendered.getByTestId('capacity-provider')).queryByText('Loading capacity providers...'),
      ).not.toBeInTheDocument(),
    );
    ['Capacity provider name 1', 'Capacity provider base 1', 'Capacity provider weight 1'].forEach((name) =>
      expect(within(rendered.getByTestId('capacity-provider')).getByLabelText(name)).toBeInTheDocument(),
    );
    expect(within(rendered.getByTestId('logging')).getByLabelText('Log driver')).toBeInTheDocument();
    ['Container name 1', 'Service registry 1', 'Container port 1'].forEach((name) =>
      expect(within(rendered.getByTestId('service-discovery')).getByLabelText(name)).toBeInTheDocument(),
    );
    [
      'Health check grace period',
      'ECS IAM instance profile',
      'Docker image credentials',
      'Fargate platform version',
      'Enable deployment circuit breaker',
      'Placement strategy',
      'Placement constraint type 1',
      'Placement constraint expression 1',
    ].forEach((name) => expect(within(rendered.getByTestId('advanced')).getByLabelText(name)).toBeInTheDocument());
  });

  it('restores Advanced Settings service, task, placement, and metadata controls', () => {
    const onFieldChange = vi.fn();
    const command = buildCommand({
      backingData: { filtered: { iamRoles: ['role'], secrets: ['secret'] } } as any,
      dockerImageCredentialsSecret: 'secret',
      enableDeploymentCircuitBreaker: true,
      healthCheckGracePeriodSeconds: 60,
      iamRole: 'role',
      placementConstraints: [{ expression: 'attribute:test', type: 'memberOf' }],
      placementStrategyName: 'BinPack CPU',
      platformVersion: '1.4.0',
      dockerLabels: { team: 'delivery' },
      environmentVariables: { ENV: 'prod' },
      tags: { owner: 'ecs' },
      useTaskDefinitionArtifact: false,
    });
    render(<AdvancedSettings {...pageProps(command, onFieldChange)} />);
    expect(screen.getByLabelText('Health check grace period')).toHaveValue(60);
    expect(screen.getByLabelText('ECS IAM instance profile')).toHaveValue('role');
    expect(screen.getByLabelText('Docker image credentials')).toHaveValue('secret');
    expect(screen.getByLabelText('Placement strategy')).toHaveValue('BinPack CPU');
    expect(screen.getByLabelText('Placement constraint type 1')).toHaveValue('memberOf');
    expect(screen.getByLabelText('Docker label name')).toHaveValue('team');
    expect(screen.getByLabelText('Environment variable name')).toHaveValue('ENV');
    expect(screen.getByLabelText('Tag name')).toHaveValue('owner');
    fireEvent.change(screen.getByLabelText('Fargate platform version'), { target: { value: 'LATEST' } });
    fireEvent.click(screen.getByLabelText('Enable deployment circuit breaker'));
    fireEvent.click(screen.getByRole('button', { name: 'Add New Placement Constraint' }));
    fireEvent.click(screen.getByRole('button', { name: 'Remove placement constraint 1' }));
    expect(onFieldChange).toHaveBeenCalledWith('platformVersion', 'LATEST');
    expect(onFieldChange).toHaveBeenCalledWith('enableDeploymentCircuitBreaker', false);
    expect(onFieldChange).toHaveBeenCalledWith('placementConstraints', [
      { expression: 'attribute:test', type: 'memberOf' },
      {},
    ]);
    expect(onFieldChange).toHaveBeenCalledWith('placementConstraints', []);
  });

  it('hides task-definition-owned advanced fields in artifact mode', () => {
    render(<AdvancedSettings {...pageProps(buildCommand({ useTaskDefinitionArtifact: true }))} />);

    expect(screen.queryByLabelText('Docker image credentials')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Docker label name')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Environment variable name')).not.toBeInTheDocument();
    expect(screen.getByText(/cannot be individually set when using a Task Definition artifact/)).toBeInTheDocument();
  });

  it('only includes target groups from the selected account through mounted backing-data loading', async () => {
    const command = buildCommand();
    const loadBalancers = [
      {
        accounts: [
          {
            name: 'account-a',
            regions: [{ name: 'eu-west-1', loadBalancers: [{ targetGroups: ['selected-target'] }] }],
          },
          {
            name: 'account-b',
            regions: [{ name: 'eu-west-1', loadBalancers: [{ targetGroups: ['other-target'] }] }],
          },
        ],
      },
    ];
    RequestBuilder.defaultHttpClient = {
      get: vi.fn((config: any) =>
        Promise.resolve(
          config.url.endsWith('loadBalancers') ? loadBalancers : config.url.endsWith('securityGroups') ? {} : [],
        ),
      ),
    } as any;
    renderModal(command);
    expect(await screen.findByRole('heading', { name: 'Container' })).toBeInTheDocument();
    fireEvent.click(screen.getAllByText('Container')[0]);
    fireEvent.click(await screen.findByRole('button', { name: 'Add New Target Group Mapping' }));
    openSelect('Target group 1');
    expect(await screen.findByRole('option', { name: 'selected-target' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'other-target' })).not.toBeInTheDocument();
  });

  it('only includes target groups from the selected region while mounted loading preserves deduplicated selections', async () => {
    const command = buildCommand({
      containerPort: 80,
      targetGroup: 'legacy',
      targetGroupMappings: [{ containerName: '', containerPort: 80, targetGroup: 'persisted' }],
    });
    const loadBalancers = [
      {
        accounts: [
          {
            name: 'account-a',
            regions: [
              { name: 'eu-west-1', loadBalancers: [{ targetGroups: ['available', 'available'] }] },
              { name: 'us-east-1', loadBalancers: [{ targetGroups: ['other-region'] }] },
            ],
          },
        ],
      },
    ];
    RequestBuilder.defaultHttpClient = {
      get: vi.fn((config: any) =>
        Promise.resolve(
          config.url.endsWith('loadBalancers') ? loadBalancers : config.url.endsWith('securityGroups') ? {} : [],
        ),
      ),
    } as any;
    renderModal(command);
    expect(await screen.findByRole('heading', { name: 'Container' })).toBeInTheDocument();
    fireEvent.click(screen.getAllByText('Container')[0]);
    openSelect('Target group 1');
    expect(await screen.findByRole('option', { name: 'available' })).toBeInTheDocument();
    expect(screen.getAllByRole('option', { name: 'persisted' }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('option', { name: 'legacy' }).length).toBeGreaterThan(0);
    expect(screen.queryByRole('option', { name: 'other-region' })).not.toBeInTheDocument();
  });

  it('adds, selects, and removes capacity providers through actual custom controls', async () => {
    const command = buildCommand({
      backingData: {
        filtered: {
          availableCapacityProviders: ['FARGATE_SPOT'],
          defaultCapacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE_SPOT', weight: 1 }],
        },
      } as any,
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE_SPOT', weight: 1 }],
      computeOption: 'capacityProviders',
      credentials: 'account',
      ecsClusterName: 'cluster',
      region: 'eu',
      useDefaultCapacityProviders: true,
    });
    const onFieldChange = vi.fn((field: string, value: any) => {
      (command as any)[field] = value;
    });
    await renderCapacityProvider(command, onFieldChange);
    fireEvent.click(screen.getByLabelText(/Use custom/));
    expect(onFieldChange).toHaveBeenCalledWith('capacityProviderStrategy', []);
    fireEvent.click(await screen.findByRole('button', { name: 'Add New Capacity Provider' }));
    const name = screen.getByLabelText('Capacity provider name 1');
    expect(name).toHaveValue('');
    fireEvent.focus(name);
    fireEvent.click(screen.getByRole('button', { name: 'FARGATE_SPOT' }));
    expect(name).toHaveValue('FARGATE_SPOT');
    fireEvent.click(screen.getByRole('button', { name: 'Remove capacity provider 1' }));
    expect(screen.queryByLabelText('Capacity provider name 1')).not.toBeInTheDocument();
  });

  it('renders and updates each custom capacity provider row by index', async () => {
    const command = buildCommand({
      capacityProviderStrategy: [
        { base: 0, capacityProvider: 'FARGATE', weight: 1 },
        { base: 1, capacityProvider: 'FARGATE_SPOT', weight: 2 },
      ],
      computeOption: 'capacityProviders',
      credentials: 'account',
      ecsClusterName: 'cluster',
      region: 'eu',
      useDefaultCapacityProviders: false,
    });
    const { onFieldChange } = await renderCapacityProvider(command);
    fireEvent.change(screen.getByLabelText('Capacity provider weight 2'), { target: { value: '3' } });
    expect(onFieldChange).toHaveBeenCalledWith(
      'capacityProviderStrategy',
      expect.arrayContaining([expect.objectContaining({ capacityProvider: 'FARGATE_SPOT', weight: 3 })]),
    );
  });

  it('renders custom capacity provider names as standard inputs', async () => {
    const command = buildCommand({
      capacityProviderStrategy: [{ base: 0, capacityProvider: 'FARGATE', weight: 1 }],
      computeOption: 'capacityProviders',
      credentials: 'account',
      ecsClusterName: 'cluster',
      region: 'eu',
      useDefaultCapacityProviders: false,
    });
    await renderCapacityProvider(command);
    expect(screen.getByLabelText('Capacity provider name 1')).toHaveAttribute('type', 'text');
    expect(screen.getByLabelText('Capacity provider name 1')).toHaveValue('FARGATE');
  });

  it('does not show custom capacity provider controls for matching default strategy values', async () => {
    const strategy = [{ base: 1, capacityProvider: 'FARGATE_SPOT', weight: 2 }];
    const command = buildCommand({
      capacityProviderStrategy: strategy,
      computeOption: 'capacityProviders',
      useDefaultCapacityProviders: true,
    });
    command.backingData.filtered.defaultCapacityProviderStrategy = strategy;
    await renderCapacityProvider(command);
    expect(screen.queryByRole('button', { name: 'Add New Capacity Provider' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('Capacity provider name 1')).toBeDisabled();
  });

  it('does not synthesize FARGATE_SPOT through actual controls when the cluster has no default strategy', async () => {
    const command = buildCommand({
      capacityProviderStrategy: [],
      computeOption: 'capacityProviders',
      useDefaultCapacityProviders: false,
    });
    const { onFieldChange } = await renderCapacityProvider(command);
    onFieldChange.mockClear();

    fireEvent.click(screen.getByLabelText(/Use cluster default/));

    expect(onFieldChange).toHaveBeenCalledWith('capacityProviderStrategy', []);
    expect(screen.getByText(/does not have capacity providers defined/)).toBeInTheDocument();
  });

  it('renders a capacity provider option only for the active custom row', async () => {
    const command = buildCommand({
      backingData: {
        ...buildCommand().backingData,
        filtered: { ...buildCommand().backingData.filtered, availableCapacityProviders: ['FARGATE', 'FARGATE_SPOT'] },
      } as any,
      capacityProviderStrategy: [
        { base: 0, capacityProvider: 'FARGATE', weight: 1 },
        { base: 1, capacityProvider: 'FARGATE_SPOT', weight: 2 },
      ],
      computeOption: 'capacityProviders',
      credentials: 'account',
      ecsClusterName: 'cluster',
      region: 'eu',
      useDefaultCapacityProviders: false,
    });
    await renderCapacityProvider(command);
    expect(screen.queryByRole('button', { name: 'FARGATE_SPOT' })).not.toBeInTheDocument();
    fireEvent.focus(screen.getByLabelText('Capacity provider name 2'));
    expect(screen.getByRole('button', { name: 'FARGATE_SPOT' })).toBeInTheDocument();
  });
});

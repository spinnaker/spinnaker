import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { UIRouterContext, UIRouterReact } from '@uirouter/react';
import React from 'react';

import {
  AccountService,
  createDeckRuntime,
  DeckRuntimeContext,
  NetworkReader,
  ReactModal,
  SubnetReader,
  TaskMonitor,
  TaskReader,
} from '@spinnaker/core';

import {
  GceCloneServerGroupModal as RoutedGceCloneServerGroupModal,
  GceCloneServerGroupModalComponent as GceCloneServerGroupModal,
  initializePipelineCreateCommand,
  reconcileGceServerGroupCommand,
  transformGceServerGroupCommand,
} from './GceCloneServerGroupModal';
import { validateGceServerGroupCommand } from './GceServerGroupWizard.helpers';
import type { IGceServerGroupCommand, IGceServerGroupWizardAdapter } from './GceServerGroupWizard.types';
import { registerGoogleProvider } from '../../../gce.module';
import { GceHealthCheckReader } from '../../../healthCheck/healthCheck.read.service';
import { GceImageReader } from '../../../image';

const application = {
  getDataSource: vi.fn(),
  name: 'fnord',
  serverGroups: { onNextRefresh: vi.fn(), refresh: vi.fn() },
} as any;

describe('GceCloneServerGroupModal', () => {
  beforeEach(() => {
    application.serverGroups.onNextRefresh.mockReset();
    application.serverGroups.refresh.mockReset();
  });

  it('opens as a wizard modal', () => {
    const props = buildProps(buildCommand());
    const runtimeServices = {} as any;
    vi.spyOn(ReactModal, 'show').mockResolvedValue(undefined);

    GceCloneServerGroupModal.show(props, runtimeServices);

    expect(ReactModal.show).toHaveBeenCalledWith(
      RoutedGceCloneServerGroupModal,
      props,
      { dialogClassName: 'wizard-modal modal-lg' },
      runtimeServices,
    );
  });

  it('renders the eight GCE pages in parity order without requiring a source template', () => {
    renderModal(buildCommand(), buildAdapter({ configureCommand: vi.fn().mockResolvedValue(buildCommand()) }));

    expect(screen.getAllByRole('listitem').map((item) => item.textContent?.trim())).toEqual([
      'Basic Settings',
      'Image',
      'Instance Type',
      'Capacity/Distribution',
      'Load Balancers',
      'Firewalls',
      'Policies',
      'Advanced Settings',
    ]);
  });

  it('shares one authoritative command state across every wizard page', () => {
    const handlers = buildInitializationHandlers();
    const configured = buildCommand(handlers);
    const reconciled = reconcileGceServerGroupCommand(buildCommand(), configured);

    Object.keys(handlers).forEach((handler) => expect(reconciled[handler]).toBe(handlers[handler]));
  });

  it('uses the registered runtime-owned GCE configuration service in the default React path', async () => {
    registerGoogleProvider();
    const runtime = createDeckRuntime(new UIRouterReact());
    const getAllSecurityGroups = vi
      .spyOn(runtime.services.securityGroupReader, 'getAllSecurityGroups')
      .mockResolvedValue({});
    const listLoadBalancers = vi.spyOn(runtime.services.loadBalancerReader, 'listLoadBalancers').mockResolvedValue([]);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockResolvedValue({});
    vi.spyOn(AccountService, 'getAllAccountDetailsForProvider').mockResolvedValue([]);
    vi.spyOn(AccountService, 'listAccounts').mockResolvedValue([]);
    vi.spyOn(NetworkReader, 'listNetworksByProvider').mockResolvedValue([]);
    vi.spyOn(SubnetReader, 'listSubnetsByProvider').mockResolvedValue([]);
    vi.spyOn(GceImageReader, 'findImages').mockResolvedValue([]);
    vi.spyOn(GceHealthCheckReader.prototype, 'listHealthChecks').mockResolvedValue([]);
    const getDelegate = vi.spyOn(runtime.services.providerServiceDelegate, 'getDelegate');

    try {
      renderModal(buildCommand({ backingData: undefined, securityGroups: [], tags: [] }), undefined, runtime.services);

      await waitFor(() => expect(listLoadBalancers).toHaveBeenCalledWith('gce'));
      await waitFor(() => expect(getAllSecurityGroups).toHaveBeenCalled());
      // Every page shares the single adapter the modal built from the runtime delegates.
      expect(getDelegate.mock.calls).toEqual([
        ['gce', 'serverGroup.commandBuilder'],
        ['gce', 'serverGroup.configurationService'],
      ]);
    } finally {
      runtime.dispose();
    }
  });

  it('exposes command validation to the wizard', async () => {
    renderModal(
      buildCommand({ credentials: '' }),
      buildAdapter({ configureCommand: vi.fn().mockResolvedValue(buildCommand({ credentials: '' })) }),
    );
    expect(await screen.findByText('Account required.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Done' })).toBeDisabled();

    expect(validateGceServerGroupCommand({ ...buildCommand(), credentials: '', capacity: { desired: null } })).toEqual(
      expect.objectContaining({
        capacity: { desired: 'Desired capacity required.' },
        credentials: 'Account required.',
      }),
    );
    expect(validateGceServerGroupCommand(buildCommand())).toEqual({});
  });

  it('turns a pipeline template-selection placeholder into an empty create command', async () => {
    const placeholder = {
      viewState: {
        disableStrategySelection: false,
        expectedArtifacts: [{ id: 'expected-image' }],
        pipeline: { id: 'pipeline' },
        requiresTemplateSelection: true,
        stage: { refId: '1' },
      },
    } as any;
    const adapter = buildAdapter({
      buildNewServerGroupCommand: vi.fn().mockResolvedValue(buildCommand()),
      configureCommand: vi
        .fn()
        .mockImplementation(async (_application: any, command: IGceServerGroupCommand) => command),
    });
    renderModal(placeholder, adapter);

    await waitFor(() => expect(adapter.configureCommand).toHaveBeenCalled());
    expect(adapter.buildNewServerGroupCommand).toHaveBeenCalledWith(application, { mode: 'createPipeline' });
    expect(adapter.configureCommand).toHaveBeenCalledWith(
      application,
      expect.objectContaining({
        credentials: 'gce-account',
        viewState: expect.objectContaining({
          disableImageSelection: true,
          expectedArtifacts: [{ id: 'expected-image' }],
          mode: 'createPipeline',
          pipeline: { id: 'pipeline' },
          showImageSourceSelector: true,
          stage: { refId: '1' },
          submitButtonLabel: 'Add',
          templatingEnabled: true,
        }),
      }),
    );
    expect(await screen.findByRole('button', { name: 'Add' })).toBeInTheDocument();
    expect(initializePipelineCreateCommand(buildCommand(), placeholder).credentials).toBe('gce-account');
  });

  it('shows a recoverable error when initialization fails', async () => {
    const adapter = buildAdapter({ configureCommand: vi.fn().mockRejectedValue(new Error('network unavailable')) });
    const props = buildProps(buildCommand({ backingData: undefined }), adapter);
    render(<GceCloneServerGroupModal {...props} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load the resources required');
    fireEvent.click(screen.getAllByRole('button', { name: 'Close' })[1]);
    expect(props.dismissModal).toHaveBeenCalled();

    adapter.configureCommand.mockResolvedValue(buildCommand());
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => expect(adapter.configureCommand).toHaveBeenCalledTimes(2));
    expect(await screen.findByRole('button', { name: 'Done' })).toBeEnabled();
    expect(screen.queryByText(/Unable to load the resources required/)).not.toBeInTheDocument();
  });

  ['create', 'clone', 'createPipeline', 'editPipeline'].forEach((mode) => {
    it(`hydrates deferred ${mode} backing data and handlers into Formik`, async () => {
      const request = deferred<IGceServerGroupCommand>();
      const writer = vi.fn().mockReturnValue(new Promise(() => undefined));
      const rendered = renderModal(
        buildCommand({ backingData: undefined, viewState: { ...buildCommand().viewState, mode } }),
        buildAdapter({ configureCommand: vi.fn().mockReturnValue(request.promise) }),
        { serverGroupWriter: { cloneServerGroup: writer } },
      );

      expect(screen.getByRole('button', { name: 'Done' })).toBeDisabled();
      const handlers = buildInitializationHandlers();
      await act(async () =>
        request.resolve(
          buildCommand({
            ...handlers,
            backingData: {
              ...buildCommand().backingData,
              filtered: { ...buildCommand().backingData.filtered, regions: ['us-central1', 'hydrated-region'] },
            },
            viewState: { ...buildCommand().viewState, mode },
          }),
        ),
      );

      const regionOptions = Array.from((await screen.findByLabelText('Region')).querySelectorAll('option')).map(
        (option) => option.value,
      );
      expect(regionOptions).toContain('hydrated-region');

      fireEvent.click(await screen.findByRole('button', { name: 'Done' }));
      await waitFor(() => expect(rendered.props.closeModal.mock.calls.length + writer.mock.calls.length).toBe(1));
      const submitted = mode.endsWith('Pipeline')
        ? rendered.props.closeModal.mock.calls[0][0]
        : writer.mock.calls[0][0];
      expect(submitted.backingData.filtered.regions).toEqual(['us-central1', 'hydrated-region']);
      Object.keys(handlers).forEach((handler) => expect(submitted[handler]).toBe(handlers[handler]));
    });
  });

  it('runs the initialization cascade in order against the latest command after configuration', async () => {
    const request = deferred<IGceServerGroupCommand>();
    renderModal(
      buildCommand({ stack: 'original', viewState: { ...buildCommand().viewState, mode: 'editPipeline' } }),
      buildAdapter({ configureCommand: vi.fn().mockReturnValue(request.promise) }),
    );
    fireEvent.change(await screen.findByLabelText('Stack'), { target: { value: 'edited' } });
    const calls: string[] = [];
    const handlers = buildInitializationHandlers((handler, command) => {
      calls.push(handler);
      expect(command.stack).toBe('edited');
    });

    await act(async () =>
      request.resolve(buildCommand({ ...handlers, viewState: { ...buildCommand().viewState, mode: 'editPipeline' } })),
    );

    expect(calls).toEqual([
      'credentialsChanged',
      'regionalChanged',
      'regionChanged',
      'networkChanged',
      'zoneChanged',
      'customInstanceChanged',
    ]);
  });

  it('preserves an untouched clone with unavailable zonal and load balancer references', () => {
    const persisted = buildCommand({
      backendServices: { 'persisted-lb': ['persisted-backend'] },
      image: 'persisted-image',
      instanceType: 'persisted-instance-type',
      loadBalancers: ['persisted-lb'],
      network: 'persisted-network',
      securityGroups: ['persisted-firewall'],
      subnet: 'persisted-subnet',
      viewState: { ...buildCommand().viewState, mode: 'clone' },
      zone: 'persisted-zone',
    });
    const reconciled = reconcileGceServerGroupCommand(
      persisted,
      buildCommand({ ...buildInitializationHandlers(), loadBalancers: [], securityGroups: [] }),
    );

    expect(reconciled).toEqual(
      expect.objectContaining({
        image: 'persisted-image',
        instanceType: 'persisted-instance-type',
        loadBalancers: ['persisted-lb'],
        network: 'persisted-network',
        securityGroups: ['persisted-firewall'],
        subnet: 'persisted-subnet',
        zone: 'persisted-zone',
      }),
    );
  });

  it('preserves an untouched pipeline with unavailable regional references and metadata', () => {
    const persisted = buildCommand({
      backendServiceMetadata: 'persisted-backend' as any,
      distributionPolicy: { targetShape: 'EVEN', zones: ['available-zone', 'persisted-zone'] },
      loadBalancerMetadata: { 'global-load-balancer-names': ['persisted-forwarding-rule'] },
      loadBalancers: ['persisted-http-lb'],
      regional: true,
      viewState: { ...buildCommand().viewState, mode: 'editPipeline' },
      zone: null,
    });
    const reconciled = reconcileGceServerGroupCommand(
      persisted,
      buildCommand({ ...buildInitializationHandlers(), loadBalancers: [], regional: true, zone: null }),
    );

    expect(reconciled.distributionPolicy.zones).toEqual(['available-zone', 'persisted-zone']);
    expect(reconciled.loadBalancers).toEqual(['persisted-http-lb']);
    expect(reconciled.loadBalancerMetadata).toEqual({
      'global-load-balancer-names': ['persisted-forwarding-rule'],
    });
  });

  it('keeps a handler-updated image when the persisted image remains available', () => {
    const handlers = buildInitializationHandlers((handler, command) => {
      if (handler === 'credentialsChanged') command.image = 'handler-image';
    });
    const reconciled = reconcileGceServerGroupCommand(
      buildCommand({ image: 'persisted-image' }),
      buildCommand({
        ...handlers,
        backingData: { ...buildCommand().backingData, allImages: [{ imageName: 'persisted-image' }] },
      }),
    );
    expect(reconciled.image).toBe('handler-image');
  });

  it('uses the configured load balancer index to normalize HTTP listener aliases once', () => {
    const handlers = buildInitializationHandlers((handler, command) => {
      if (handler === 'credentialsChanged') command.loadBalancers = ['http-url-map'];
    });
    const reconciled = reconcileGceServerGroupCommand(
      buildCommand({
        loadBalancerMetadata: { 'global-load-balancer-names': ['http-listener'] },
        loadBalancers: ['http-listener'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      }),
      buildCommand({
        ...handlers,
        backingData: {
          accounts: ['gce-account'],
          filtered: {
            ...buildCommand().backingData.filtered,
            loadBalancerIndex: {
              'http-url-map': {
                listeners: [{ name: 'http-listener' }],
                loadBalancerType: 'HTTP',
                name: 'http-url-map',
              },
            },
          },
        },
      }),
    );
    expect(reconciled.loadBalancers).toEqual(['http-url-map']);
  });

  it('restores an unavailable clone image from viewState.imageId', () => {
    const reconciled = reconcileGceServerGroupCommand(
      buildCommand({
        image: undefined,
        viewState: { ...buildCommand().viewState, imageId: 'retired-image', mode: 'clone' },
      }),
      buildCommand({ ...buildInitializationHandlers(), image: null }),
    );
    expect(reconciled.image).toBe('retired-image');
    expect(reconciled.viewState.imageId).toBe('retired-image');
  });

  it('preserves flat backend metadata for an unavailable load balancer without creating an empty mapping', () => {
    const reconciled = reconcileGceServerGroupCommand(
      buildCommand({
        backendServiceMetadata: 'persisted-backend-a, persisted-backend-b' as any,
        backendServices: undefined,
        loadBalancers: ['unavailable-http-lb'],
      }),
      buildCommand({ ...buildInitializationHandlers(), loadBalancers: [] }),
    );
    expect(reconciled.backendServices).toBeUndefined();
    expect(transformGceServerGroupCommand(reconciled).instanceMetadata['backend-service-names']).toBe(
      'persisted-backend-a,persisted-backend-b',
    );
  });

  it('preserves an unavailable clone account and region without running account-dependent handlers', () => {
    const handlers = buildInitializationHandlers();
    const reconciled = reconcileGceServerGroupCommand(
      buildCommand({
        credentials: 'retired-account',
        region: 'retired-region',
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      }),
      buildCommand({ ...handlers }),
    );
    expect(reconciled.credentials).toBe('retired-account');
    expect(reconciled.region).toBe('retired-region');
    Object.values(handlers).forEach((handler) => expect(handler).not.toHaveBeenCalled());
  });

  it('preserves an unavailable pipeline region after initializing the available account', () => {
    const handlers = buildInitializationHandlers();
    const reconciled = reconcileGceServerGroupCommand(
      buildCommand({ region: 'retired-region', viewState: { ...buildCommand().viewState, mode: 'editPipeline' } }),
      buildCommand({ ...handlers }),
    );
    expect(reconciled.region).toBe('retired-region');
    expect(handlers.credentialsChanged).toHaveBeenCalledTimes(1);
  });

  it('hydrates regions before checking an available persisted region and runs all handlers in order', () => {
    const calls: string[] = [];
    const handlers = buildInitializationHandlers((handler, command) => {
      calls.push(handler);
      if (handler === 'credentialsChanged') command.backingData.filtered.regions = ['us-central1'];
    });
    reconcileGceServerGroupCommand(
      buildCommand(),
      buildCommand({ ...handlers, backingData: { accounts: ['gce-account'], filtered: {} } }),
    );
    expect(calls).toEqual([
      'credentialsChanged',
      'regionalChanged',
      'regionChanged',
      'networkChanged',
      'zoneChanged',
      'customInstanceChanged',
    ]);
  });

  it('rebuilds clone load balancer and backend metadata from edited selections', () => {
    const transformed = transformGceServerGroupCommand(
      buildCommand({
        backendServices: { 'current-http-lb': ['current-backend'] },
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'current-http-lb': { listeners: [{ name: 'current-forwarding-rule' }], loadBalancerType: 'HTTP' },
            },
          },
        },
        instanceMetadata: { 'backend-service-names': 'stale-backend', owner: 'delivery' },
        loadBalancers: ['current-http-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      }),
    );
    expect(transformed.instanceMetadata).toEqual({
      'backend-service-names': 'current-backend',
      'global-load-balancer-names': 'current-forwarding-rule',
      owner: 'delivery',
    });
  });

  it('rebuilds pipeline metadata from the selected regional load balancer and backend services', () => {
    const transformed = transformGceServerGroupCommand(
      buildCommand({
        backingData: {
          filtered: { loadBalancerIndex: { 'regional-lb': { loadBalancerType: 'NETWORK', name: 'regional-lb' } } },
        },
        backendServices: { 'regional-lb': ['backend-a', 'backend-b'] },
        loadBalancers: ['regional-lb'],
        viewState: { ...buildCommand().viewState, mode: 'editPipeline' },
      }),
    );
    expect(transformed.instanceMetadata).toEqual({
      'backend-service-names': 'backend-a,backend-b',
      'load-balancer-names': 'regional-lb',
    });
  });

  ['createPipeline', 'editPipeline'].forEach((mode) => {
    it(`returns transformed WizardModal values without executing infrastructure in ${mode} mode`, async () => {
      const writer = vi.fn();
      const rendered = renderModal(
        buildCommand({
          capacity: { desired: '${ parameters.desired }' } as any,
          minCpuPlatform: '(Automatic)',
          tags: [{ value: 'web' }, 'api'],
          viewState: { ...buildCommand().viewState, mode },
        }),
        buildAdapter(),
        { serverGroupWriter: { cloneServerGroup: writer } },
      );
      expect(await screen.findByRole('button', { name: 'Done' })).toBeEnabled();

      fireEvent.click(screen.getByRole('button', { name: 'Done' }));

      await waitFor(() => expect(rendered.props.closeModal).toHaveBeenCalled());
      expect(rendered.props.closeModal).toHaveBeenCalledWith(
        expect.objectContaining({
          capacity: {
            desired: '${ parameters.desired }',
            max: '${ parameters.desired }',
            min: '${ parameters.desired }',
          },
          minCpuPlatform: '',
          tags: ['web', 'api'],
          targetSize: '${ parameters.desired }',
        }),
      );
      expect(writer).not.toHaveBeenCalled();
    });
  });

  ['create', 'clone'].forEach((mode) => {
    it(`transforms and submits ${mode} commands through TaskMonitor`, async () => {
      const writer = vi.fn().mockResolvedValue({ id: 'task-id' });
      const monitorSubmit = vi.spyOn(TaskMonitor.prototype, 'startSubmit');
      vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockReturnValue(new Promise(() => undefined));
      const rendered = renderModal(
        buildCommand({
          autoscalingPolicy: {
            maxNumReplicas: 6,
            minNumReplicas: 2,
            coolDownPeriodSec: 60,
            cpuUtilization: { utilizationTarget: 0.5 },
          },
          instanceMetadata: { owner: 'delivery' },
          loadBalancerMetadata: {
            'global-load-balancer-names': ['stale-global-forwarding-rule'],
            'load-balancer-names': ['stale-regional-forwarding-rule'],
          },
          loadBalancers: [
            { loadBalancerType: 'TCP', name: 'global-forwarding-rule' },
            { loadBalancerType: 'INTERNAL_MANAGED', listeners: [{ name: 'regional-forwarding-rule' }] },
          ],
          securityGroups: ['firewall-id'],
          viewState: { ...buildCommand().viewState, mode },
        }),
        buildAdapter(),
        { serverGroupWriter: { cloneServerGroup: writer } },
      );
      expect(await screen.findByRole('button', { name: 'Done' })).toBeEnabled();

      fireEvent.change(screen.getByLabelText('Detail'), { target: { value: `${mode}-detail` } });
      fireEvent.click(screen.getByRole('button', { name: 'Done' }));

      await waitFor(() =>
        expect(writer).toHaveBeenCalledWith(
          expect.objectContaining({
            autoscalingPolicy: expect.objectContaining({ maxNumReplicas: 6, minNumReplicas: 2 }),
            capacity: { desired: 3, max: 6, min: 2 },
            freeFormDetails: `${mode}-detail`,
            instanceMetadata: {
              owner: 'delivery',
              'global-load-balancer-names': 'global-forwarding-rule',
              'load-balancer-names': 'regional-forwarding-rule',
            },
            targetSize: 3,
          }),
          application,
        ),
      );
      expect(monitorSubmit).toHaveBeenCalled();
      const submitted = writer.mock.lastCall[0];
      expect(submitted.loadBalancerMetadata).toBeUndefined();
      expect(submitted.securityGroups).toBeUndefined();
      expect(rendered.props.closeModal).not.toHaveBeenCalled();
    });
  });

  it('submits the immediate Formik command instead of stale component state', async () => {
    const rendered = renderModal(
      buildCommand({ stack: 'edited', viewState: { ...buildCommand().viewState, mode: 'editPipeline' } }),
      buildAdapter(),
    );
    expect(await screen.findByRole('button', { name: 'Done' })).toBeEnabled();
    fireEvent.change(screen.getByLabelText('Stack'), { target: { value: 'latest' } });

    fireEvent.click(screen.getByRole('button', { name: 'Done' }));

    await waitFor(() => expect(rendered.props.closeModal).toHaveBeenCalled());
    expect(rendered.props.closeModal).toHaveBeenCalledWith(expect.objectContaining({ stack: 'latest' }));
  });

  it.each([
    [
      'merges refreshed backing data into Formik edits made while configuration is loading',
      async () => {
        const request = deferred<IGceServerGroupCommand>();
        const adapter = buildAdapter({ configureCommand: vi.fn().mockReturnValue(request.promise) });
        const rendered = renderModal(
          buildCommand({
            unknownReference: 'keep-me',
            viewState: { ...buildCommand().viewState, mode: 'editPipeline' },
          }),
          adapter,
        );
        fireEvent.change(await screen.findByLabelText('Stack'), { target: { value: 'edited' } });
        const regionChanged = vi.fn();

        await act(async () =>
          request.resolve(
            buildCommand({
              backingData: { ...buildCommand().backingData, refreshed: true },
              regionChanged,
              stack: 'old',
              unknownReference: 'keep-me',
              viewState: { ...buildCommand().viewState, mode: 'editPipeline' },
            }),
          ),
        );
        fireEvent.click(await screen.findByRole('button', { name: 'Done' }));

        await waitFor(() => expect(rendered.props.closeModal).toHaveBeenCalled());
        expect(rendered.props.closeModal).toHaveBeenCalledWith(
          expect.objectContaining({
            backingData: expect.objectContaining({ refreshed: true }),
            stack: 'edited',
            unknownReference: 'keep-me',
          }),
        );
        expect(rendered.props.closeModal.mock.calls[0][0].regionChanged).toBe(regionChanged);
      },
    ],
    [
      'keeps the latest rendered command when overlapping configuration requests finish out of order',
      async () => {
        const first = deferred<IGceServerGroupCommand>();
        const second = deferred<IGceServerGroupCommand>();
        const adapter = buildAdapter({
          configureCommand: vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise),
        });
        const rendered = renderModal(
          buildCommand({ stack: 'first-request', viewState: { ...buildCommand().viewState, mode: 'editPipeline' } }),
          adapter,
        );

        rendered.rerenderCommand(
          buildCommand({ stack: 'second-request', viewState: { ...buildCommand().viewState, mode: 'editPipeline' } }),
        );
        await waitFor(() => expect(adapter.configureCommand).toHaveBeenCalledTimes(2));
        await act(async () =>
          second.resolve(
            buildCommand({
              backingData: {
                ...buildCommand().backingData,
                filtered: { ...buildCommand().backingData.filtered, regions: ['second-region'] },
              },
            }),
          ),
        );
        await act(async () =>
          first.resolve(
            buildCommand({
              backingData: {
                ...buildCommand().backingData,
                filtered: { ...buildCommand().backingData.filtered, regions: ['first-region'] },
              },
            }),
          ),
        );

        expect(screen.getByLabelText('Stack')).toHaveValue('second-request');
        const regionOptions = Array.from(screen.getByLabelText('Region').querySelectorAll('option')).map(
          (option) => option.value,
        );
        expect(regionOptions).toContain('second-region');
        expect(regionOptions).not.toContain('first-region');
      },
    ],
  ] as const)('%s', async (_description, verify) => verify());

  it.each(['resolve', 'reject'] as const)(
    'does not reconcile, update Formik, invoke callbacks, or warn after configuration %s following unmount',
    async (outcome) => {
      const request = deferred<IGceServerGroupCommand>();
      const handlers = buildInitializationHandlers();
      const adapter = buildAdapter({ configureCommand: vi.fn().mockReturnValue(request.promise) });
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
      const rendered = renderModal(buildCommand(), adapter);
      await screen.findByLabelText('Stack');

      rendered.unmount();
      await act(async () => {
        if (outcome === 'resolve') {
          request.resolve(buildCommand({ ...handlers, backingData: { filtered: { regions: ['late'] } } }));
        } else {
          request.reject(new Error('late configuration failure'));
        }
        await request.promise.catch(() => undefined);
      });

      Object.values(handlers).forEach((handler) => expect(handler).not.toHaveBeenCalled());
      expect(rendered.props.closeModal).not.toHaveBeenCalled();
      expect(rendered.props.dismissModal).not.toHaveBeenCalled();
      expect(consoleError.mock.calls.flat().join(' ')).not.toMatch(/unmounted component/i);
      consoleError.mockRestore();
    },
  );

  it('renders actual TaskMonitor submission status inside WizardModal', async () => {
    const task = deferred<any>();
    renderModal(buildCommand(), buildAdapter(), { serverGroupWriter: { cloneServerGroup: () => task.promise } });
    fireEvent.click(await screen.findByRole('button', { name: 'Done' }));

    await waitFor(() => expect(screen.getAllByRole('heading', { name: 'Configure GCE server group' })).toHaveLength(2));
  });

  it('retains the modal command when infrastructure submission fails', async () => {
    const rendered = renderModal(buildCommand(), buildAdapter(), {
      serverGroupWriter: { cloneServerGroup: vi.fn().mockRejectedValue({ failureMessage: 'create failed' }) },
    });
    fireEvent.click(await screen.findByRole('button', { name: 'Done' }));

    expect(await screen.findByText('create failed')).toBeInTheDocument();
    expect(rendered.props.closeModal).not.toHaveBeenCalled();
    expect(rendered.props.dismissModal).not.toHaveBeenCalled();
  });

  it('refreshes and navigates to the created server group after TaskMonitor completion', async () => {
    const task = cloneTask();
    const writer = vi.fn().mockResolvedValue(task);
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue(task);
    const refreshCallbacks: Array<() => void> = [];
    application.serverGroups.onNextRefresh.mockImplementation((callback: () => void) => {
      refreshCallbacks.push(callback);
      return vi.fn();
    });
    const stateService = { go: vi.fn(), includes: vi.fn((state: string) => state === '**.clusters') };
    renderModal(buildCommand(), buildAdapter(), { serverGroupWriter: { cloneServerGroup: writer } }, { stateService });
    fireEvent.click(await screen.findByRole('button', { name: 'Done' }));
    await waitFor(() => expect(application.serverGroups.refresh).toHaveBeenCalled());
    expect(application.serverGroups.onNextRefresh.mock.invocationCallOrder[0]).toBeLessThan(
      application.serverGroups.refresh.mock.invocationCallOrder[0],
    );

    refreshCallbacks[0]();

    expect(stateService.go).toHaveBeenCalledWith('.serverGroup', {
      accountId: 'gce-account',
      provider: 'gce',
      region: 'us-central1',
      serverGroup: 'fnord-main-api-v042',
    });
  });

  it('unsubscribes from the application refresh after the callback runs', async () => {
    const unsubscribe = vi.fn();
    let refreshCallback: (() => void) | undefined;
    application.serverGroups.onNextRefresh.mockImplementation((callback: () => void) => {
      refreshCallback = callback;
      return unsubscribe;
    });
    const task = cloneTask();
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue(task);
    renderModal(buildCommand(), buildAdapter(), {
      serverGroupWriter: { cloneServerGroup: vi.fn().mockResolvedValue(task) },
    });
    fireEvent.click(await screen.findByRole('button', { name: 'Done' }));
    await waitFor(() => expect(application.serverGroups.refresh).toHaveBeenCalled());
    expect(unsubscribe).not.toHaveBeenCalled();

    refreshCallback?.();

    expect(unsubscribe).toHaveBeenCalledTimes(1);
  });

  it('ignores a late application refresh after unmount', async () => {
    const unsubscribe = vi.fn();
    let refreshCallback: (() => void) | undefined;
    application.serverGroups.onNextRefresh.mockImplementation((callback: () => void) => {
      refreshCallback = callback;
      return unsubscribe;
    });
    const task = cloneTask();
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue(task);
    const stateService = { go: vi.fn(), includes: vi.fn() };
    const rendered = renderModal(
      buildCommand(),
      buildAdapter(),
      { serverGroupWriter: { cloneServerGroup: vi.fn().mockResolvedValue(task) } },
      { stateService },
    );
    fireEvent.click(await screen.findByRole('button', { name: 'Done' }));
    await waitFor(() => expect(application.serverGroups.refresh).toHaveBeenCalled());

    rendered.unmount();
    refreshCallback?.();

    expect(unsubscribe).toHaveBeenCalledTimes(1);
    expect(stateService.go).not.toHaveBeenCalled();
    expect(rendered.props.closeModal).not.toHaveBeenCalled();
    expect(rendered.props.dismissModal).not.toHaveBeenCalled();
  });

  it('unsubscribes from a pending application refresh before replacing it', async () => {
    const firstUnsubscribe = vi.fn();
    const secondUnsubscribe = vi.fn();
    application.serverGroups.onNextRefresh.mockReturnValueOnce(firstUnsubscribe).mockReturnValueOnce(secondUnsubscribe);
    const task = cloneTask();
    vi.spyOn(TaskReader, 'waitUntilTaskCompletes').mockResolvedValue(task);
    let monitor: TaskMonitor | undefined;
    const handleTaskSuccess = TaskMonitor.prototype.handleTaskSuccess;
    vi.spyOn(TaskMonitor.prototype, 'handleTaskSuccess').mockImplementation(function (
      this: TaskMonitor,
      ...args: Parameters<TaskMonitor['handleTaskSuccess']>
    ) {
      monitor = this;
      return handleTaskSuccess.apply(this, args);
    });
    renderModal(buildCommand(), buildAdapter(), {
      serverGroupWriter: { cloneServerGroup: vi.fn().mockResolvedValue(task) },
    });
    fireEvent.click(await screen.findByRole('button', { name: 'Done' }));
    await waitFor(() => expect(application.serverGroups.onNextRefresh).toHaveBeenCalledTimes(1));

    // A second task completion arrives before the first refresh callback has fired.
    await act(async () => monitor!.handleTaskSuccess(task));
    await waitFor(() => expect(application.serverGroups.onNextRefresh).toHaveBeenCalledTimes(2));

    expect(firstUnsubscribe).toHaveBeenCalledTimes(1);
    expect(secondUnsubscribe).not.toHaveBeenCalled();
  });
});

function renderModal(
  command: IGceServerGroupCommand,
  adapter: IGceServerGroupWizardAdapter | undefined,
  runtimeServices: Record<string, any> = { serverGroupWriter: { cloneServerGroup: vi.fn() } },
  overrides: Record<string, any> = {},
) {
  const props = { ...buildProps(command, adapter), ...overrides };
  const router = new UIRouterReact();
  const element = (nextProps = props) => (
    <UIRouterContext.Provider value={router}>
      <DeckRuntimeContext.Provider value={{ services: runtimeServices } as any}>
        <GceCloneServerGroupModal {...nextProps} />
      </DeckRuntimeContext.Provider>
    </UIRouterContext.Provider>
  );
  const rendered = render(element());
  return {
    ...rendered,
    props,
    rerenderCommand(nextCommand: IGceServerGroupCommand) {
      rendered.rerender(element({ ...props, command: nextCommand }));
    },
  };
}

function buildCommand(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    application: 'fnord',
    backingData: {
      accounts: ['gce-account'],
      allImages: [{ imageName: 'ubuntu' }],
      filtered: {
        cpuPlatforms: ['(Automatic)'],
        images: ['ubuntu'],
        instanceTypes: ['n1-standard-1'],
        networks: ['default'],
        regions: ['us-central1'],
        subnets: ['default'],
        zones: ['us-central1-a'],
      },
      persistentDiskTypes: ['pd-ssd'],
    },
    capacity: { desired: 3, max: 3, min: 3 },
    credentials: 'gce-account',
    disks: [{ sizeGb: 10, type: 'pd-ssd' }],
    distributionPolicy: { targetShape: 'EVEN', zones: [] },
    freeFormDetails: 'api',
    image: 'ubuntu',
    instanceMetadata: {},
    instanceType: 'n1-standard-1',
    loadBalancers: [],
    network: 'default',
    region: 'us-central1',
    regional: false,
    securityGroups: [],
    stack: 'main',
    subnet: 'default',
    tags: [],
    viewState: { dirty: {}, disableImageSelection: false, mode: 'create', useSimpleCapacity: true },
    zone: 'us-central1-a',
    ...overrides,
  };
}

function buildProps(command: IGceServerGroupCommand, adapter?: IGceServerGroupWizardAdapter): any {
  return {
    adapter,
    application,
    closeModal: vi.fn(),
    command,
    dismissModal: vi.fn(),
    router: {},
    stateParams: {},
    stateService: { go: vi.fn(), includes: () => false },
    title: 'Configure GCE server group',
  };
}

function buildAdapter(overrides: Record<string, any> = {}): any {
  return {
    applyCommandHandler: vi.fn(),
    applyConfigurationRefresh: vi.fn(),
    applyConfigurationUpdate: vi.fn(),
    buildNewServerGroupCommand: vi.fn(),
    configureCommand: vi.fn().mockResolvedValue(buildCommand()),
    ...overrides,
  };
}

function buildInitializationHandlers(
  onCall: (handler: string, command: IGceServerGroupCommand) => void = () => undefined,
): Record<string, ReturnType<typeof vi.fn>> {
  return [
    'credentialsChanged',
    'regionalChanged',
    'regionChanged',
    'networkChanged',
    'zoneChanged',
    'customInstanceChanged',
  ].reduce((handlers, handler) => {
    handlers[handler] = vi.fn().mockImplementation((command: IGceServerGroupCommand) => {
      onCall(handler, command);
      return { dirty: {} };
    });
    return handlers;
  }, {} as Record<string, ReturnType<typeof vi.fn>>);
}

function cloneTask(): any {
  return {
    execution: {
      stages: [
        {
          context: { 'deploy.server.groups': { 'us-central1': 'fnord-main-api-v042' } },
          type: 'cloneServerGroup',
        },
      ],
    },
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, reject, resolve };
}

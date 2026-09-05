import { cloneDeep } from 'lodash';
import React from 'react';
import { mount, shallow } from 'enzyme';
import { UIRouterReact } from '@uirouter/react';

import {
  AccountService,
  createDeckRuntime,
  NetworkReader,
  ReactModal,
  SubnetReader,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';

import {
  GceCloneServerGroupModal as RoutedGceCloneServerGroupModal,
  GceCloneServerGroupModalComponent as GceCloneServerGroupModal,
  transformGceServerGroupCommand,
} from './GceCloneServerGroupModal';
import { validateGceServerGroupCommand } from './GceServerGroupWizard.helpers';
import { GceServerGroupWizardAdapter } from './GceServerGroupWizardAdapter';
import type { IGceServerGroupCommand, IGceServerGroupWizardAdapter } from './GceServerGroupWizard.types';
import { registerGoogleProvider } from '../../../gce.module';
import { GceHealthCheckReader } from '../../../healthCheck/healthCheck.read.service';
import { GceImageReader } from '../../../image';

const application = {
  name: 'fnord',
  serverGroups: {
    onNextRefresh: jasmine.createSpy('onNextRefresh'),
    refresh: jasmine.createSpy('refresh'),
  },
} as any;

describe('GceCloneServerGroupModal', () => {
  beforeEach(() => {
    application.serverGroups.onNextRefresh.calls.reset();
    application.serverGroups.refresh.calls.reset();
  });

  it('opens as a wizard modal', () => {
    const props = buildProps(buildCommand());
    const runtimeServices = {} as any;
    spyOn(ReactModal, 'show').and.returnValue(Promise.resolve());

    GceCloneServerGroupModal.show(props, runtimeServices);

    expect(ReactModal.show).toHaveBeenCalledWith(
      RoutedGceCloneServerGroupModal,
      props,
      { dialogClassName: 'wizard-modal modal-lg' },
      runtimeServices,
    );
  });

  it('renders the eight GCE pages in parity order without requiring a source template', () => {
    const wrapper = shallow(<GceCloneServerGroupModal {...buildProps(buildCommand(), buildAdapter())} />, {
      disableLifecycleMethods: true,
    } as any);

    const wizard = wrapper.find(WizardModal);
    expect(wizard.exists()).toBe(true);
    const pages = shallow(
      <div>
        {wizard.prop('render')({
          formik: { values: buildCommand() } as any,
          nextIdx: (() => {
            let index = 0;
            return () => ++index;
          })(),
          wizard: {} as any,
        })}
      </div>,
    );

    expect(pages.find(WizardPage).map((page) => page.prop('label'))).toEqual([
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
    const adapter = buildAdapter();
    const wrapper = shallow(<GceCloneServerGroupModal {...buildProps(buildCommand(), adapter)} />, {
      disableLifecycleMethods: true,
    } as any);
    const wizard = wrapper.find(WizardModal);
    const pages = shallow(
      <div>
        {wizard.prop('render')({
          formik: { values: buildCommand() } as any,
          nextIdx: () => 1,
          wizard: {} as any,
        })}
      </div>,
    );
    const commandStates = pages.find(WizardPage).map((page) => {
      const renderedPage = shallow(
        <div>{page.prop('render')({ innerRef: React.createRef(), onLoadingChanged: () => undefined })}</div>,
      );
      return renderedPage.childAt(0).prop('commandState');
    });

    expect(commandStates.every((commandState) => commandState === commandStates[0])).toBe(true);
    expect(commandStates[0]).toBeDefined();
  });

  it('uses the registered runtime-owned GCE configuration service in the default React path', async () => {
    registerGoogleProvider();
    const runtime = createDeckRuntime(new UIRouterReact());
    const getAllSecurityGroups = spyOn(runtime.services.securityGroupReader, 'getAllSecurityGroups').and.resolveTo({});
    const listLoadBalancers = spyOn(runtime.services.loadBalancerReader, 'listLoadBalancers').and.resolveTo([]);
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.resolveTo({});
    spyOn(AccountService, 'getAllAccountDetailsForProvider').and.resolveTo([]);
    spyOn(AccountService, 'listAccounts').and.resolveTo([]);
    spyOn(NetworkReader, 'listNetworksByProvider').and.resolveTo([]);
    spyOn(SubnetReader, 'listSubnetsByProvider').and.resolveTo([]);
    spyOn(GceImageReader, 'findImages').and.resolveTo([]);
    spyOn(GceHealthCheckReader.prototype, 'listHealthChecks').and.resolveTo([]);
    const getDelegate = spyOn(runtime.services.providerServiceDelegate, 'getDelegate').and.callThrough();

    try {
      const modal = new GceCloneServerGroupModal(buildProps(buildCommand())) as any;
      modal.context = runtime;
      const wizard = modal.render() as React.ReactElement<any>;
      const pages = shallow(
        <div>
          {wizard.props.render({
            formik: { values: buildCommand() } as any,
            nextIdx: () => 1,
            wizard: {} as any,
          })}
        </div>,
      );
      const adapters = pages.find(WizardPage).map((page) => {
        const renderedPage = shallow(
          <div>{page.prop('render')({ innerRef: React.createRef(), onLoadingChanged: () => undefined })}</div>,
        );
        return renderedPage.childAt(0).prop('adapter');
      });

      expect(getDelegate.calls.allArgs()).toEqual([
        ['gce', 'serverGroup.commandBuilder'],
        ['gce', 'serverGroup.configurationService'],
      ]);
      expect(adapters.every((adapter) => adapter === adapters[0])).toBe(true);
      expect(adapters[0]).toEqual(jasmine.any(GceServerGroupWizardAdapter));

      const configuredCommand = await adapters[0].configureCommand(
        application,
        buildCommand({ backingData: undefined, securityGroups: [], tags: [] }),
      );
      await adapters[0].applyConfigurationRefresh(configuredCommand, 'refreshLoadBalancers');
      await adapters[0].applyConfigurationRefresh(configuredCommand, 'refreshSecurityGroups');

      expect(getAllSecurityGroups).toHaveBeenCalledTimes(2);
      expect(listLoadBalancers).toHaveBeenCalledTimes(2);
      expect(listLoadBalancers).toHaveBeenCalledWith('gce');
    } finally {
      runtime.dispose();
    }
  });

  it('exposes command validation to the wizard', () => {
    const wrapper = shallow(<GceCloneServerGroupModal {...buildProps(buildCommand(), buildAdapter())} />, {
      disableLifecycleMethods: true,
    } as any);
    const validate = wrapper.find(WizardModal).prop('validate');

    expect(validate({ ...buildCommand(), credentials: '', capacity: { desired: null } } as any)).toEqual(
      jasmine.objectContaining({
        capacity: { desired: 'Desired capacity required.' },
        credentials: 'Account required.',
      }),
    );
    expect(validate(buildCommand())).toEqual({});
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
    const adapter = buildAdapter();
    adapter.buildNewServerGroupCommand.and.resolveTo(buildCommand());
    adapter.configureCommand.and.callFake(async (_application: any, command: IGceServerGroupCommand) => command);
    const modal = new GceCloneServerGroupModal(buildProps(placeholder, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

    await modal.configureCommand();

    expect(adapter.buildNewServerGroupCommand).toHaveBeenCalledWith(application, { mode: 'createPipeline' });
    expect(adapter.configureCommand).toHaveBeenCalledWith(
      application,
      jasmine.objectContaining({
        credentials: 'gce-account',
        viewState: jasmine.objectContaining({
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
    expect(modal.state.command.credentials).toBe('gce-account');
  });

  it('shows a recoverable error when initialization fails', async () => {
    const adapter = buildAdapter();
    let initializationError = true;
    adapter.configureCommand.and.callFake(() =>
      initializationError ? Promise.reject(new Error('network unavailable')) : Promise.resolve(buildCommand()),
    );
    const props = buildProps(buildCommand({ backingData: undefined }), adapter);
    const modal = new GceCloneServerGroupModal(props) as any;
    spyOn(modal, 'setState').and.callFake((state: any, callback?: () => void) => {
      modal.state = { ...modal.state, ...state };
      callback?.();
    });

    await modal.configureCommand();

    const errorState = shallow(modal.render());
    expect(errorState.find('.gce-server-group-initialization-error').text()).toContain(
      'Unable to load the resources required to configure this server group. Check your connection and try again.',
    );
    errorState.find('.gce-server-group-initialization-close').simulate('click');
    expect(props.dismissModal).toHaveBeenCalled();

    initializationError = false;
    await modal.retryConfiguration();
    expect(adapter.configureCommand).toHaveBeenCalledTimes(2);
    expect(modal.state.initializationError).toBe(false);
    expect(modal.state.loaded).toBe(true);
  });

  ['create', 'clone', 'createPipeline', 'editPipeline'].forEach((mode) => {
    it(`hydrates deferred ${mode} backing data and handlers into Formik`, async () => {
      const command = buildCommand({
        backingData: undefined,
        viewState: { ...buildCommand().viewState, mode },
      });
      const request = deferred<IGceServerGroupCommand>();
      const adapter = buildAdapter();
      adapter.configureCommand.and.returnValue(request.promise);
      const wrapper = mount(<GceCloneServerGroupModal {...buildProps(command, adapter)} />);

      expect(wrapper.find(WizardModal).key()).toBe('loading');
      const handlers = buildInitializationHandlers();
      request.resolve(
        buildCommand({
          ...handlers,
          backingData: { filtered: { regions: ['hydrated-region'] } },
          viewState: { ...buildCommand().viewState, mode },
        }),
      );
      await request.promise;
      wrapper.update();

      const wizard = wrapper.find(WizardModal);
      const formikValues = (wizard.instance() as WizardModal<IGceServerGroupCommand>).formik.values;
      expect(wizard.key()).toBe('loaded');
      expect(formikValues.backingData.filtered.regions).toEqual(['hydrated-region']);
      Object.keys(handlers).forEach((handler) => expect(formikValues[handler]).toBe(handlers[handler]));
      wrapper.unmount();
    });
  });

  it('runs the initialization cascade in order against the latest command after configuration', async () => {
    const command = buildCommand({ stack: 'original' });
    const request = deferred<IGceServerGroupCommand>();
    const adapter = buildAdapter();
    adapter.configureCommand.and.returnValue(request.promise);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));
    const formik: any = {
      setValues: jasmine
        .createSpy('setValues')
        .and.callFake((values: IGceServerGroupCommand) => (formik.values = values)),
      values: buildCommand({ stack: 'original' }),
    };
    modal.formik = formik;
    const calls: string[] = [];
    const handlers = buildInitializationHandlers((handler, latestCommand) => {
      calls.push(handler);
      expect(latestCommand.stack).toBe('edited');
    });

    const configure = modal.configureCommand();
    formik.values = buildCommand({ stack: 'edited' });
    request.resolve(buildCommand(handlers));
    await configure;

    expect(calls).toEqual([
      'credentialsChanged',
      'regionalChanged',
      'regionChanged',
      'networkChanged',
      'zoneChanged',
      'customInstanceChanged',
    ]);
    Object.keys(handlers).forEach((handler) => expect(handlers[handler]).toHaveBeenCalledWith(formik.values));
  });

  it('retains the original load balancer baseline when selection changes while configuration is pending', async () => {
    const command = buildCommand({
      loadBalancerMetadata: {
        'load-balancer-names': ['orphan-listener'],
      },
      loadBalancers: ['original-lb'],
      viewState: { ...buildCommand().viewState, mode: 'clone' },
    });
    const request = deferred<IGceServerGroupCommand>();
    const adapter = buildAdapter();
    adapter.configureCommand.and.returnValue(request.promise);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));
    const formik: any = {
      setValues: jasmine
        .createSpy('setValues')
        .and.callFake((values: IGceServerGroupCommand) => (formik.values = values)),
      values: command,
    };
    modal.formik = formik;

    const configure = modal.configureCommand();
    formik.values = {
      ...formik.values,
      loadBalancers: ['edited-lb'],
    };
    request.resolve(
      buildCommand({
        ...buildInitializationHandlers(),
        backingData: {
          ...buildCommand().backingData,
          filtered: {
            ...buildCommand().backingData.filtered,
            loadBalancerIndex: {},
          },
        },
      }),
    );
    await configure;

    expect(modal.state.command.loadBalancers).toEqual(['edited-lb']);
    expect(modal.state.command.viewState.initialLoadBalancers).toEqual(['original-lb']);
    expect(validateGceServerGroupCommand(modal.state.command).loadBalancers).toContain(
      'Saved load balancer metadata cannot be mapped',
    );
  });

  it('normalizes the immutable original alias baseline after an edit during configuration', async () => {
    const command = buildCommand({
      backingData: undefined,
      loadBalancerMetadata: {
        'global-load-balancer-names': ['http-listener'],
      },
      loadBalancers: ['http-listener'],
      viewState: { ...buildCommand().viewState, mode: 'clone' },
    });
    const request = deferred<IGceServerGroupCommand>();
    const adapter = buildAdapter();
    adapter.configureCommand.and.returnValue(request.promise);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));
    const formik: any = {
      setValues: jasmine
        .createSpy('setValues')
        .and.callFake((values: IGceServerGroupCommand) => (formik.values = values)),
      values: command,
    };
    modal.formik = formik;
    const handlers = buildInitializationHandlers((handler, initializedCommand) => {
      if (handler === 'credentialsChanged') {
        initializedCommand.backingData.filtered.loadBalancerIndex = {
          'http-url-map': {
            listeners: [{ name: 'http-listener' }],
            loadBalancerType: 'HTTP',
            name: 'http-url-map',
          },
        };
      }
    });

    const configure = modal.configureCommand();
    formik.values = {
      ...formik.values,
      loadBalancers: ['edited-lb'],
    };
    request.resolve(
      buildCommand({
        ...handlers,
        backingData: {
          ...buildCommand().backingData,
          filtered: { ...buildCommand().backingData.filtered },
        },
      }),
    );
    await configure;

    expect(modal.state.command.loadBalancers).toEqual(['edited-lb']);
    expect(modal.state.command.viewState.initialLoadBalancers).toEqual(['http-url-map']);
    expect(validateGceServerGroupCommand(modal.state.command).loadBalancers).toContain(
      'Saved load balancer metadata cannot be mapped',
    );

    const revertedCommand = {
      ...modal.state.command,
      loadBalancers: ['http-url-map'],
    };
    expect(validateGceServerGroupCommand(revertedCommand).loadBalancers).toBeUndefined();
  });

  it('retains the immutable original baseline when configuration fails after an edit and retry succeeds', async () => {
    const command = buildCommand({
      loadBalancerMetadata: {
        'load-balancer-names': ['orphan-listener'],
      },
      loadBalancers: ['original-lb'],
      viewState: { ...buildCommand().viewState, mode: 'clone' },
    });
    const firstRequest = deferred<IGceServerGroupCommand>();
    const secondRequest = deferred<IGceServerGroupCommand>();
    const adapter = buildAdapter();
    adapter.configureCommand.and.returnValues(firstRequest.promise, secondRequest.promise);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));
    const formik: any = {
      setValues: jasmine
        .createSpy('setValues')
        .and.callFake((values: IGceServerGroupCommand) => (formik.values = values)),
      values: command,
    };
    modal.formik = formik;

    const configure = modal.configureCommand();
    formik.values = {
      ...formik.values,
      loadBalancers: ['edited-lb'],
    };
    firstRequest.reject(new Error('configuration failed'));
    await configure;

    const retry = modal.retryConfiguration();
    secondRequest.resolve(
      buildCommand({
        ...buildInitializationHandlers(),
        backingData: {
          ...buildCommand().backingData,
          filtered: {
            ...buildCommand().backingData.filtered,
            loadBalancerIndex: {},
          },
        },
      }),
    );
    await retry;

    expect(modal.state.command.loadBalancers).toEqual(['edited-lb']);
    expect(modal.state.command.viewState.initialLoadBalancers).toEqual(['original-lb']);
    expect(validateGceServerGroupCommand(modal.state.command).loadBalancers).toContain(
      'Saved load balancer metadata cannot be mapped',
    );
  });

  it('preserves an untouched clone with unavailable zonal and load balancer references', async () => {
    const persistedImplicitFirewall = {
      id: 'persisted-implicit-firewall',
      name: 'persisted-implicit-firewall',
      network: 'persisted-network',
      targetTags: ['persisted-tag'],
    };
    const command = buildCommand({
      backingData: {
        allImages: [{ imageName: 'persisted-image' }],
        filtered: {
          cpuPlatforms: ['persisted-cpu-platform'],
          healthChecks: [{ kind: 'http', name: 'persisted-health-check', selfLink: 'persisted-health-check-url' }],
          instanceTypes: ['persisted-instance-type'],
          loadBalancerIndex: {
            'known-lb': {
              backendServices: ['known-backend-old'],
              listeners: [{ name: 'known-listener-old' }],
              loadBalancerType: 'HTTP',
              name: 'known-lb',
            },
            'persisted-lb': {
              backendServices: ['persisted-backend'],
              listeners: [{ name: 'persisted-listener' }],
              loadBalancerType: 'INTERNAL_MANAGED',
              name: 'persisted-lb',
            },
          },
          loadBalancers: ['known-lb', 'persisted-lb'],
          networks: ['persisted-network'],
          securityGroups: [{ id: 'known-firewall' }, { id: 'persisted-firewall' }],
          subnets: ['persisted-subnet'],
          zones: ['persisted-zone'],
        },
      },
      autoHealingPolicy: {
        healthCheck: 'persisted-health-check',
        healthCheckKind: 'http',
        healthCheckUrl: 'persisted-health-check-url',
        initialDelaySec: 300,
      },
      backendServiceMetadata: 'known-backend-old, persisted-backend' as any,
      backendServices: {
        'known-lb': ['known-backend-old'],
        'persisted-lb': ['persisted-backend'],
      },
      image: 'persisted-image',
      implicitSecurityGroups: [persistedImplicitFirewall],
      instanceMetadata: { owner: 'delivery' },
      instanceType: 'persisted-instance-type',
      loadBalancerMetadata: {
        'global-load-balancer-names': ['known-listener-old'],
        'load-balancer-names': ['persisted-listener'],
      },
      loadBalancers: ['known-lb', 'persisted-lb'],
      minCpuPlatform: 'persisted-cpu-platform',
      network: 'persisted-network',
      securityGroups: ['known-firewall', 'persisted-firewall'],
      subnet: 'persisted-subnet',
      viewState: { ...buildCommand().viewState, mode: 'clone' },
      zone: 'persisted-zone',
    });
    const handlers = buildInitializationHandlers((handler, initializedCommand) => {
      if (handler !== 'credentialsChanged') {
        return;
      }
      initializedCommand.instanceType = null;
      delete initializedCommand.minCpuPlatform;
      initializedCommand.zone = 'available-zone';
      initializedCommand.loadBalancers = ['known-lb'];
      initializedCommand.backendServices = { 'known-lb': ['known-backend-new'] };
      initializedCommand.backendServiceMetadata = ['known-backend-new'];
      initializedCommand.image = 'available-image';
      initializedCommand.network = 'available-network';
      initializedCommand.subnet = 'available-subnet';
      initializedCommand.securityGroups = ['known-firewall'];
      initializedCommand.implicitSecurityGroups = [
        { id: 'known-implicit-firewall', network: 'available-network', targetTags: [] },
      ];
      initializedCommand.autoHealingPolicy = {
        ...initializedCommand.autoHealingPolicy,
        healthCheck: 'available-health-check',
        healthCheckKind: 'https',
        healthCheckUrl: 'available-health-check-url',
      };
      initializedCommand.loadBalancerMetadata = {
        'global-load-balancer-names': ['known-listener-new'],
      };
    });
    const configured = buildCommand({
      ...handlers,
      backingData: {
        accounts: ['gce-account'],
        allImages: [{ imageName: 'available-image' }],
        filtered: {
          cpuPlatforms: ['(Automatic)'],
          healthChecks: [{ kind: 'https', name: 'available-health-check', selfLink: 'available-health-check-url' }],
          instanceTypes: ['available-instance-type'],
          loadBalancerIndex: {
            'known-lb': {
              backendServices: ['known-backend-new'],
              listeners: [{ name: 'known-listener-new' }],
              loadBalancerType: 'HTTP',
              name: 'known-lb',
            },
          },
          loadBalancers: ['known-lb'],
          networks: [{ id: 'available-network' }],
          regions: ['us-central1'],
          securityGroups: [{ id: 'known-firewall' }],
          subnets: [{ id: 'available-subnet' }],
          zones: ['available-zone'],
        },
      },
    });
    const adapter = buildAdapter();
    adapter.configureCommand.and.resolveTo(configured);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

    await modal.configureCommand();

    expect(modal.state.command).toEqual(
      jasmine.objectContaining({
        autoHealingPolicy: {
          healthCheck: 'persisted-health-check',
          healthCheckKind: 'http',
          healthCheckUrl: 'persisted-health-check-url',
          initialDelaySec: 300,
        },
        backendServiceMetadata: ['known-backend-new', 'persisted-backend'],
        backendServices: {
          'known-lb': ['known-backend-new'],
          'persisted-lb': ['persisted-backend'],
        },
        image: 'persisted-image',
        implicitSecurityGroups: [
          { id: 'known-implicit-firewall', network: 'available-network', targetTags: [] },
          persistedImplicitFirewall,
        ],
        instanceType: 'persisted-instance-type',
        loadBalancerMetadata: {
          'global-load-balancer-names': ['known-listener-new'],
          'load-balancer-names': ['persisted-listener'],
        },
        loadBalancers: ['known-lb', 'persisted-lb'],
        minCpuPlatform: 'persisted-cpu-platform',
        network: 'persisted-network',
        securityGroups: ['known-firewall', 'persisted-firewall'],
        subnet: 'persisted-subnet',
        zone: 'persisted-zone',
      }),
    );
    expect(transformGceServerGroupCommand(modal.state.command).instanceMetadata).toEqual({
      'backend-service-names': 'known-backend-new,persisted-backend',
      'global-load-balancer-names': 'known-listener-new',
      'load-balancer-names': 'persisted-listener',
      owner: 'delivery',
    });
  });

  it('preserves an untouched pipeline with unavailable regional references and metadata', async () => {
    const persistedImplicitFirewall = {
      id: 'pipeline-implicit-firewall',
      name: 'pipeline-implicit-firewall',
      network: 'pipeline-network',
      targetTags: ['pipeline-tag'],
    };
    const command = buildCommand({
      autoHealingPolicy: {
        healthCheck: 'pipeline-health-check',
        healthCheckKind: 'tcp',
        healthCheckUrl: 'pipeline-health-check-url',
        initialDelaySec: 120,
      },
      backendServiceMetadata: 'persisted-backend' as any,
      backendServices: { 'persisted-http-lb': ['persisted-backend'] },
      distributionPolicy: { targetShape: 'EVEN', zones: ['available-zone', 'persisted-zone'] },
      image: 'pipeline-image',
      implicitSecurityGroups: [persistedImplicitFirewall],
      instanceType: 'persisted-instance-type',
      loadBalancerMetadata: {
        'global-load-balancer-names': ['persisted-forwarding-rule'],
      },
      loadBalancers: ['persisted-http-lb'],
      minCpuPlatform: 'persisted-cpu-platform',
      network: 'pipeline-network',
      regional: true,
      securityGroups: ['pipeline-firewall'],
      selectZones: true,
      subnet: 'pipeline-subnet',
      viewState: { ...buildCommand().viewState, mode: 'editPipeline' },
      zone: null,
    });
    const handlers = buildInitializationHandlers((handler, initializedCommand) => {
      if (handler !== 'credentialsChanged') {
        return;
      }
      initializedCommand.instanceType = null;
      delete initializedCommand.minCpuPlatform;
      initializedCommand.distributionPolicy.zones = ['available-zone'];
      initializedCommand.loadBalancers = [];
      initializedCommand.backendServices = {};
      initializedCommand.backendServiceMetadata = [];
      initializedCommand.image = 'available-image';
      initializedCommand.network = 'available-network';
      initializedCommand.subnet = 'available-subnet';
      initializedCommand.securityGroups = [];
      initializedCommand.implicitSecurityGroups = [];
      delete initializedCommand.autoHealingPolicy.healthCheck;
      delete initializedCommand.autoHealingPolicy.healthCheckKind;
      initializedCommand.loadBalancerMetadata = {};
    });
    const configured = buildCommand({
      ...handlers,
      backingData: {
        allImages: [{ imageName: 'available-image' }],
        filtered: {
          cpuPlatforms: ['(Automatic)'],
          healthChecks: [{ kind: 'http', name: 'available-health-check' }],
          instanceTypes: ['available-instance-type'],
          loadBalancerIndex: {},
          loadBalancers: [],
          networks: ['available-network'],
          securityGroups: [],
          subnets: ['available-subnet'],
          zones: ['available-zone'],
        },
      },
    });
    const adapter = buildAdapter();
    adapter.configureCommand.and.resolveTo(configured);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

    await modal.configureCommand();

    expect(modal.state.command).toEqual(
      jasmine.objectContaining({
        autoHealingPolicy: {
          healthCheck: 'pipeline-health-check',
          healthCheckKind: 'tcp',
          healthCheckUrl: 'pipeline-health-check-url',
          initialDelaySec: 120,
        },
        backendServiceMetadata: ['persisted-backend'],
        backendServices: { 'persisted-http-lb': ['persisted-backend'] },
        distributionPolicy: { targetShape: 'EVEN', zones: ['available-zone', 'persisted-zone'] },
        image: 'pipeline-image',
        implicitSecurityGroups: [persistedImplicitFirewall],
        instanceType: 'persisted-instance-type',
        loadBalancerMetadata: {
          'global-load-balancer-names': ['persisted-forwarding-rule'],
        },
        loadBalancers: ['persisted-http-lb'],
        minCpuPlatform: 'persisted-cpu-platform',
        network: 'pipeline-network',
        securityGroups: ['pipeline-firewall'],
        subnet: 'pipeline-subnet',
      }),
    );
    expect(transformGceServerGroupCommand(modal.state.command).instanceMetadata).toEqual({
      'backend-service-names': 'persisted-backend',
      'global-load-balancer-names': 'persisted-forwarding-rule',
    });
  });

  it('keeps a handler-updated image when the persisted image remains available', async () => {
    const baseCommand = buildCommand();
    const command = buildCommand({
      backingData: {
        ...baseCommand.backingData,
        allImages: [{ imageName: 'persisted-image' }],
      },
      image: 'persisted-image',
    });
    const handlers = buildInitializationHandlers((handler, initializedCommand) => {
      if (handler === 'credentialsChanged') {
        initializedCommand.image = 'handler-image';
      }
    });
    const configured = buildCommand({
      ...handlers,
      backingData: {
        ...baseCommand.backingData,
        allImages: [{ imageName: 'persisted-image' }],
      },
    });
    const adapter = buildAdapter();
    adapter.configureCommand.and.resolveTo(configured);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

    await modal.configureCommand();

    expect(modal.state.command.image).toBe('handler-image');
  });

  it('uses the configured load balancer index to normalize HTTP listener aliases once', async () => {
    const command = buildCommand({
      backingData: undefined,
      loadBalancerMetadata: { 'global-load-balancer-names': ['http-listener'] },
      loadBalancers: ['http-listener'],
      viewState: { ...buildCommand().viewState, mode: 'clone' },
    });
    const handlers = buildInitializationHandlers((handler, initializedCommand) => {
      if (handler === 'credentialsChanged') {
        initializedCommand.backingData.filtered.loadBalancerIndex = {
          'http-url-map': {
            account: 'gce-account',
            listeners: [{ name: 'http-listener' }],
            loadBalancerType: 'HTTP',
            name: 'http-url-map',
          },
        };
        initializedCommand.loadBalancers = ['http-url-map'];
      }
    });
    const configured = buildCommand({
      ...handlers,
      backingData: {
        ...buildCommand().backingData,
        filtered: { ...buildCommand().backingData.filtered },
      },
      loadBalancers: ['http-listener'],
    });
    const adapter = buildAdapter();
    adapter.configureCommand.and.resolveTo(configured);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

    await modal.configureCommand();

    expect(modal.state.command.loadBalancers).toEqual(['http-url-map']);
    expect(modal.state.command.viewState.initialLoadBalancers).toEqual(['http-url-map']);
    expect(validateGceServerGroupCommand(modal.state.command).loadBalancers).toBeUndefined();
    const transformed = transformGceServerGroupCommand(modal.state.command);
    expect(transformed.instanceMetadata).toEqual({
      'global-load-balancer-names': 'http-listener',
    });
    // The wizard's URL-map display identity must not reach the request: a global HTTP LB attaches
    // through global-load-balancer-names, and 'http-url-map' names nothing GCEUtil can resolve.
    expect(transformed.loadBalancers).toEqual([]);
  });

  (['SSL', 'TCP'] as const).forEach((loadBalancerType) => {
    (['clone', 'createPipeline', 'editPipeline'] as const).forEach((mode) => {
      it(`submits unavailable global ${loadBalancerType} selections in ${mode} mode`, async () => {
        const loadBalancerName = `unavailable-${loadBalancerType.toLowerCase()}-lb`;
        const baseCommand = buildCommand();
        const command = buildCommand({
          backingData: {
            ...baseCommand.backingData,
            filtered: {
              ...baseCommand.backingData.filtered,
              loadBalancerIndex: {
                [loadBalancerName]: {
                  loadBalancerType,
                  name: loadBalancerName,
                },
              },
            },
          },
          loadBalancerMetadata: {
            'global-load-balancer-names': [loadBalancerName],
          },
          loadBalancers: [loadBalancerName],
          viewState: { ...baseCommand.viewState, mode },
        });
        const configured = buildCommand({
          ...buildInitializationHandlers(),
          backingData: {
            ...baseCommand.backingData,
            filtered: {
              ...baseCommand.backingData.filtered,
              loadBalancerIndex: {},
            },
          },
        });
        const adapter = buildAdapter();
        adapter.configureCommand.and.resolveTo(configured);
        const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
        spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

        await modal.configureCommand();

        const transformed = transformGceServerGroupCommand(modal.state.command);
        expect(transformed.instanceMetadata).toEqual({
          'global-load-balancer-names': loadBalancerName,
        });
        expect(transformed.loadBalancers).toEqual([loadBalancerName]);
      });
    });
  });

  it('restores an unavailable clone image from viewState.imageId', async () => {
    const command = buildCommand({
      backingData: undefined,
      image: undefined,
      viewState: { ...buildCommand().viewState, imageId: 'retired-image', mode: 'clone' },
    });
    const handlers = buildInitializationHandlers((handler, initializedCommand) => {
      if (handler === 'credentialsChanged') {
        initializedCommand.image = null;
      }
    });
    const configured = buildCommand({
      ...handlers,
      backingData: {
        ...buildCommand().backingData,
        allImages: [{ imageName: 'available-image' }],
      },
    });
    const adapter = buildAdapter();
    adapter.configureCommand.and.resolveTo(configured);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

    await modal.configureCommand();

    expect(modal.state.command.image).toBe('retired-image');
    expect(modal.state.command.viewState.imageId).toBe('retired-image');
  });

  it('preserves flat backend metadata for an unavailable load balancer without creating an empty mapping', async () => {
    const command = buildCommand({
      backendServiceMetadata: 'persisted-backend-a, persisted-backend-b' as any,
      backendServices: undefined,
      loadBalancers: ['unavailable-http-lb'],
      viewState: { ...buildCommand().viewState, mode: 'clone' },
    });
    const configured = buildCommand({
      ...buildInitializationHandlers(),
      backingData: {
        ...buildCommand().backingData,
        filtered: {
          ...buildCommand().backingData.filtered,
          loadBalancerIndex: {},
          loadBalancers: [],
        },
      },
    });
    const adapter = buildAdapter();
    adapter.configureCommand.and.resolveTo(configured);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

    await modal.configureCommand();

    expect(modal.state.command.backendServices).toBeUndefined();
    expect(transformGceServerGroupCommand(modal.state.command).instanceMetadata['backend-service-names']).toBe(
      'persisted-backend-a,persisted-backend-b',
    );
  });

  it('preserves an unavailable clone account and region without running account-dependent handlers', async () => {
    const command = buildCommand({
      backingData: {
        ...buildCommand().backingData,
        accounts: ['retired-account'],
        filtered: { ...buildCommand().backingData.filtered, regions: ['retired-region'] },
      },
      credentials: 'retired-account',
      region: 'retired-region',
      viewState: { ...buildCommand().viewState, mode: 'clone' },
    });
    const handlers = buildInitializationHandlers();
    const configured = buildCommand({
      ...handlers,
      backingData: {
        ...buildCommand().backingData,
        accounts: ['gce-account'],
        filtered: { ...buildCommand().backingData.filtered, regions: ['us-central1'] },
      },
    });
    const adapter = buildAdapter();
    adapter.configureCommand.and.resolveTo(configured);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

    await modal.configureCommand();

    expect(modal.state.command.credentials).toBe('retired-account');
    expect(modal.state.command.region).toBe('retired-region');
    Object.keys(handlers).forEach((handler) => expect(handlers[handler]).not.toHaveBeenCalled());
  });

  it('preserves an unavailable pipeline region after initializing the available account', async () => {
    const command = buildCommand({
      backingData: {
        ...buildCommand().backingData,
        filtered: { ...buildCommand().backingData.filtered, regions: ['retired-region'] },
      },
      region: 'retired-region',
      viewState: { ...buildCommand().viewState, mode: 'editPipeline' },
    });
    const handlers = buildInitializationHandlers();
    const configured = buildCommand({
      ...handlers,
      backingData: {
        ...buildCommand().backingData,
        filtered: { ...buildCommand().backingData.filtered, regions: ['us-central1'] },
      },
    });
    const adapter = buildAdapter();
    adapter.configureCommand.and.resolveTo(configured);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

    await modal.configureCommand();

    expect(modal.state.command.credentials).toBe('gce-account');
    expect(modal.state.command.region).toBe('retired-region');
    expect(handlers.credentialsChanged).toHaveBeenCalledOnceWith(modal.state.command);
    ['regionalChanged', 'regionChanged', 'networkChanged', 'zoneChanged', 'customInstanceChanged'].forEach((handler) =>
      expect(handlers[handler]).not.toHaveBeenCalled(),
    );
  });

  it('hydrates regions before checking an available persisted region and runs all handlers in order', async () => {
    const command = buildCommand();
    const calls: string[] = [];
    const handlers = buildInitializationHandlers((handler, initializedCommand) => {
      calls.push(handler);
      if (handler === 'credentialsChanged') {
        initializedCommand.backingData.filtered.regions = ['us-central1'];
      }
    });
    const configured = buildCommand({
      ...handlers,
      backingData: {
        ...buildCommand().backingData,
        filtered: {},
      },
    });
    const adapter = buildAdapter();
    adapter.configureCommand.and.resolveTo(configured);
    const modal = new GceCloneServerGroupModal(buildProps(command, adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

    await modal.configureCommand();

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
              'current-http-lb': {
                listeners: [{ name: 'current-forwarding-rule' }],
                loadBalancerType: 'HTTP',
              },
            },
          },
        },
        instanceMetadata: {
          'backend-service-names': 'stale-backend',
          'global-load-balancer-names': 'stale-global-rule',
          'load-balancer-names': 'stale-regional-rule',
          owner: 'delivery',
        },
        loadBalancerMetadata: {
          'global-load-balancer-names': ['persisted-global-rule'],
          'load-balancer-names': ['persisted-regional-rule'],
        },
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

  describe('transformGceServerGroupCommand load balancer submission', () => {
    it('submits EXTERNAL_MANAGED listener names in loadBalancers and regional metadata', () => {
      const command = buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'external-managed-lb': {
                listeners: [{ name: 'external-listener-a' }, { name: 'external-listener-b' }],
                loadBalancerType: 'EXTERNAL_MANAGED',
                name: 'external-managed-lb',
              },
            },
          },
        },
        loadBalancers: ['external-managed-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      expect(transformed.loadBalancers).toEqual(['external-listener-a', 'external-listener-b']);
      expect(transformed.instanceMetadata['load-balancer-names']).toBe('external-listener-a,external-listener-b');
      expect(command.loadBalancers).toEqual(['external-managed-lb']);
    });

    it('submits REGIONAL_EXTERNAL_NETWORK direct names in loadBalancers and regional metadata', () => {
      const command = buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'regional-network-lb': {
                loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
                name: 'regional-network-lb',
              },
            },
          },
        },
        loadBalancers: ['regional-network-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      expect(transformed.loadBalancers).toEqual(['regional-network-lb']);
      expect(transformed.instanceMetadata['load-balancer-names']).toBe('regional-network-lb');
      expect(command.loadBalancers).toEqual(['regional-network-lb']);
    });

    it('submits INTERNAL direct names in loadBalancers and regional metadata', () => {
      const command = buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'internal-lb': { loadBalancerType: 'INTERNAL', name: 'internal-lb' },
            },
          },
        },
        loadBalancers: ['internal-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      expect(transformed.loadBalancers).toEqual(['internal-lb']);
      expect(transformed.instanceMetadata['load-balancer-names']).toBe('internal-lb');
      expect(command.loadBalancers).toEqual(['internal-lb']);
    });

    it('submits INTERNAL_MANAGED listener names in loadBalancers and regional metadata', () => {
      const command = buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'internal-managed-lb': {
                listeners: [{ name: 'internal-listener-a' }, { name: 'internal-listener-b' }],
                loadBalancerType: 'INTERNAL_MANAGED',
                name: 'internal-managed-lb',
              },
            },
          },
        },
        loadBalancers: ['internal-managed-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      expect(transformed.loadBalancers).toEqual(['internal-listener-a', 'internal-listener-b']);
      expect(transformed.instanceMetadata['load-balancer-names']).toBe('internal-listener-a,internal-listener-b');
      expect(command.loadBalancers).toEqual(['internal-managed-lb']);
    });

    it('preserves selected load balancer metadata insertion order', () => {
      const command = buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'internal-managed-lb': {
                listeners: [{ name: 'z-listener' }, { name: 'a-listener' }],
                loadBalancerType: 'INTERNAL_MANAGED',
                name: 'internal-managed-lb',
              },
            },
          },
        },
        loadBalancers: ['internal-managed-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      expect(transformed.loadBalancers).toEqual(['z-listener', 'a-listener']);
      expect(transformed.instanceMetadata['load-balancer-names']).toBe('z-listener,a-listener');
    });

    it('submits an unresolvable selection by name when no metadata explains it', () => {
      const command = buildCommand({
        backingData: { filtered: { loadBalancerIndex: {} } },
        loadBalancers: ['unresolvable-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      // Dropping it would deploy a server group that silently takes no traffic; sending the raw name
      // lets GCEUtil.queryAllLoadBalancers fail the deploy instead.
      expect(transformed.loadBalancers).toEqual(['unresolvable-lb']);
    });

    it('does not seed load balancer metadata from the source instanceMetadata', () => {
      const command = buildCommand({
        backingData: { filtered: { loadBalancerIndex: {} } },
        instanceMetadata: {
          'load-balancer-names': 'deselected-lb',
          owner: 'delivery',
        },
        loadBalancers: ['unresolvable-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      // Clouddriver appends to whatever we send, so seeding from the source server group's raw
      // metadata would silently reattach a load balancer the user deselected.
      expect(transformed.loadBalancers).toEqual(['unresolvable-lb']);
      expect(transformed.instanceMetadata).toEqual({ owner: 'delivery' });
    });

    it('submits global SSL and TCP names from global metadata in loadBalancers', () => {
      const command = buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'global-ssl-lb': { loadBalancerType: 'SSL', name: 'global-ssl-lb' },
              'global-tcp-lb': { loadBalancerType: 'TCP', name: 'global-tcp-lb' },
            },
          },
        },
        loadBalancers: ['global-ssl-lb', 'global-tcp-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      expect(transformed.loadBalancers).toEqual(['global-ssl-lb', 'global-tcp-lb']);
      expect(transformed.instanceMetadata['global-load-balancer-names']).toBe('global-ssl-lb,global-tcp-lb');
      expect(command.loadBalancers).toEqual(['global-ssl-lb', 'global-tcp-lb']);
    });

    (['clone', 'editPipeline'] as const).forEach((mode) => {
      it(`submits inline typed global SSL and TCP selections in ${mode} mode`, () => {
        const command = buildCommand({
          backingData: { filtered: { loadBalancerIndex: {} } },
          loadBalancers: [
            { loadBalancerType: 'SSL', name: 'inline-ssl-lb' },
            { loadBalancerType: 'TCP', name: 'inline-tcp-lb' },
          ],
          viewState: { ...buildCommand().viewState, mode },
        });
        const originalCommand = cloneDeep(command);

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.loadBalancers).toEqual(['inline-ssl-lb', 'inline-tcp-lb']);
        expect(transformed.instanceMetadata['global-load-balancer-names']).toBe('inline-ssl-lb,inline-tcp-lb');
        expect(command).toEqual(originalCommand);
      });
    });

    it('submits regional, SSL, then TCP names regardless of load balancer index order', () => {
      const command = buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'global-tcp-lb': { loadBalancerType: 'TCP', name: 'global-tcp-lb' },
              'regional-network-lb': {
                loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
                name: 'regional-network-lb',
              },
              'global-ssl-lb': { loadBalancerType: 'SSL', name: 'global-ssl-lb' },
            },
          },
        },
        loadBalancers: ['global-tcp-lb', 'regional-network-lb', 'global-ssl-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      expect(transformed.loadBalancers).toEqual(['regional-network-lb', 'global-ssl-lb', 'global-tcp-lb']);
      expect(command.loadBalancers).toEqual(['global-tcp-lb', 'regional-network-lb', 'global-ssl-lb']);
    });

    it('does not submit global HTTP URL-map display identities as top-level loadBalancers', () => {
      const command = buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'http-url-map': {
                listeners: [{ name: 'http-forwarding-rule' }],
                loadBalancerType: 'HTTP',
                name: 'http-url-map',
              },
            },
          },
        },
        loadBalancers: ['http-url-map'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      expect(transformed.loadBalancers).toEqual([]);
      expect(transformed.instanceMetadata['global-load-balancer-names']).toBe('http-forwarding-rule');
      expect(command.loadBalancers).toEqual(['http-url-map']);
    });

    it('preserves unavailable regional listener metadata in loadBalancers without mutating the wizard command', () => {
      const command = buildCommand({
        loadBalancerMetadata: {
          'global-load-balancer-names': ['known-listener-new'],
          'load-balancer-names': ['persisted-listener'],
        },
        loadBalancers: ['known-lb', 'persisted-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      expect(transformed.loadBalancers).toEqual(['persisted-listener', 'known-lb', 'persisted-lb']);
      expect(transformed.instanceMetadata).toEqual({
        'global-load-balancer-names': 'known-listener-new',
        'load-balancer-names': 'persisted-listener',
      });
      expect(command.loadBalancers).toEqual(['known-lb', 'persisted-lb']);
    });

    describe('load balancer metadata attribution', () => {
      it('retains raw unresolved names for unexplained unavailable selections while sibling metadata exists', () => {
        const command = buildCommand({
          backingData: {
            filtered: {
              loadBalancerIndex: {
                'resolved-http-lb': {
                  listeners: [{ name: 'resolved-listener' }],
                  loadBalancerType: 'HTTP',
                  name: 'resolved-http-lb',
                },
              },
            },
          },
          loadBalancerMetadata: {
            'global-load-balancer-names': ['resolved-listener'],
          },
          loadBalancerSelectionMetadata: {
            'resolved-http-lb': {
              key: 'global-load-balancer-names',
              names: ['resolved-listener'],
            },
          },
          loadBalancers: ['resolved-http-lb', 'unexplained-lb'],
          viewState: { ...buildCommand().viewState, mode: 'clone' },
        });

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.loadBalancers).toEqual(['unexplained-lb']);
        expect(transformed.instanceMetadata).toEqual({
          'global-load-balancer-names': 'resolved-listener',
        });
      });

      (['clone', 'createPipeline', 'editPipeline'] as const).forEach((mode) => {
        it(`submits every selected load balancer in ${mode} mode`, () => {
          const command = buildCommand({
            backingData: {
              filtered: {
                loadBalancerIndex: {
                  'resolved-http-lb': {
                    listeners: [{ name: 'resolved-listener' }],
                    loadBalancerType: 'HTTP',
                    name: 'resolved-http-lb',
                  },
                },
              },
            },
            loadBalancerSelectionMetadata: {
              'resolved-http-lb': {
                key: 'global-load-balancer-names',
                names: ['resolved-listener'],
              },
            },
            loadBalancers: ['resolved-http-lb', 'unexplained-lb'],
            viewState: { ...buildCommand().viewState, mode },
          });

          const transformed = transformGceServerGroupCommand(command);

          expect(transformed.loadBalancers).toEqual(['unexplained-lb']);
        });
      });

      it('omits deselected unavailable load balancer names from submission payloads', () => {
        const command = buildCommand({
          backingData: { filtered: { loadBalancerIndex: {} } },
          loadBalancerMetadata: {
            'load-balancer-names': ['listener-a', 'listener-b'],
          },
          loadBalancerSelectionMetadata: {
            'unavailable-a': {
              key: 'load-balancer-names',
              names: ['listener-a'],
            },
          },
          loadBalancers: ['unavailable-a'],
          viewState: {
            ...buildCommand().viewState,
            mode: 'clone',
            initialLoadBalancers: ['unavailable-a', 'unavailable-b'],
          },
        });

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.loadBalancers).toEqual(['listener-a']);
        expect(transformed.instanceMetadata).toEqual({
          'load-balancer-names': 'listener-a',
        });
        expect(transformed.loadBalancerSelectionMetadata).toBeUndefined();
      });

      it('preserves attributed and residual legacy metadata in mixed partial unchanged state', () => {
        const command = buildCommand({
          backingData: { filtered: { loadBalancerIndex: {} } },
          loadBalancerMetadata: {
            'load-balancer-names': ['attributed-listener', 'orphan-listener'],
          },
          loadBalancerSelectionMetadata: {
            'unavailable-a': {
              key: 'load-balancer-names',
              names: ['attributed-listener'],
            },
          },
          loadBalancers: ['unavailable-a'],
          viewState: {
            ...buildCommand().viewState,
            mode: 'clone',
            initialLoadBalancers: ['unavailable-a'],
            loadBalancerSelectionsChanged: false,
          },
        });

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.instanceMetadata).toEqual({
          'load-balancer-names': 'attributed-listener,orphan-listener',
        });
        expect(transformed.loadBalancers).toEqual(['attributed-listener']);
      });

      it('preserves residual legacy metadata for an unchanged resolved attributed selection', () => {
        const command = buildCommand({
          backingData: {
            filtered: {
              loadBalancerIndex: {
                'regional-network-lb': {
                  loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
                  name: 'regional-network-lb',
                },
              },
            },
          },
          loadBalancerMetadata: {
            'load-balancer-names': ['regional-network-lb', 'orphan-listener'],
          },
          loadBalancerSelectionMetadata: {
            'regional-network-lb': {
              key: 'load-balancer-names',
              names: ['regional-network-lb'],
            },
          },
          loadBalancers: ['regional-network-lb'],
          viewState: {
            ...buildCommand().viewState,
            initialLoadBalancers: ['regional-network-lb'],
            mode: 'clone',
          },
        });

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.loadBalancers).toEqual(['regional-network-lb']);
        expect(transformed.instanceMetadata).toEqual({
          'load-balancer-names': 'regional-network-lb,orphan-listener',
        });
      });

      it('submits resolved regional selections alongside partial per-selection metadata', () => {
        const command = buildCommand({
          backingData: {
            filtered: {
              loadBalancerIndex: {
                'regional-network-lb': {
                  loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
                  name: 'regional-network-lb',
                },
              },
            },
          },
          loadBalancerSelectionMetadata: {
            'unavailable-a': {
              key: 'load-balancer-names',
              names: ['attributed-listener'],
            },
          },
          loadBalancers: ['unavailable-a', 'regional-network-lb'],
          viewState: { ...buildCommand().viewState, mode: 'clone' },
        });

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.loadBalancers).toEqual(['attributed-listener', 'regional-network-lb']);
        expect(transformed.instanceMetadata).toEqual({
          'load-balancer-names': 'attributed-listener,regional-network-lb',
        });
      });

      it('fails loud for multiple unresolved legacy selections with ambiguous partial flat metadata', () => {
        const command = buildCommand({
          backingData: { filtered: { loadBalancerIndex: {} } },
          loadBalancerMetadata: {
            'load-balancer-names': ['legacy-listener'],
          },
          loadBalancers: ['unresolved-a', 'unresolved-b'],
          viewState: { ...buildCommand().viewState, mode: 'clone' },
        });

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.loadBalancers).toEqual(['legacy-listener', 'unresolved-a', 'unresolved-b']);
        expect(transformed.instanceMetadata).toEqual({
          'load-balancer-names': 'legacy-listener',
        });
      });

      it('fails loud when resolved sibling metadata cannot explain one unresolved legacy selection', () => {
        const command = buildCommand({
          backingData: {
            filtered: {
              loadBalancerIndex: {
                'resolved-http-lb': {
                  listeners: [{ name: 'resolved-listener' }],
                  loadBalancerType: 'HTTP',
                  name: 'resolved-http-lb',
                },
              },
            },
          },
          loadBalancers: ['resolved-http-lb', 'unresolved-lb'],
          viewState: { ...buildCommand().viewState, mode: 'clone' },
        });

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.loadBalancers).toEqual(['unresolved-lb']);
        expect(transformed.instanceMetadata).toEqual({
          'global-load-balancer-names': 'resolved-listener',
        });
      });

      it('fails loud for an unavailable selection with ambiguous legacy global attribution', () => {
        const command = buildCommand({
          backingData: { filtered: { loadBalancerIndex: {} } },
          loadBalancerMetadata: {
            'global-load-balancer-names': ['legacy-global-listener'],
          },
          loadBalancerSelectionMetadata: {
            'unavailable-global-lb': {
              key: 'global-load-balancer-names',
              names: ['legacy-global-listener'],
            },
          },
          loadBalancers: ['unavailable-global-lb'],
          viewState: { ...buildCommand().viewState, mode: 'clone' },
        });

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.loadBalancers).toEqual(['unavailable-global-lb']);
        expect(transformed.instanceMetadata).toEqual({
          'global-load-balancer-names': 'legacy-global-listener',
        });
      });

      it('does not submit residual global metadata matching an unselected indexed proxy load balancer', () => {
        const command = buildCommand({
          backingData: {
            filtered: {
              loadBalancerIndex: {
                'unselected-ssl-lb': {
                  loadBalancerType: 'SSL',
                  name: 'unselected-ssl-lb',
                },
              },
            },
          },
          loadBalancerMetadata: {
            'global-load-balancer-names': ['unselected-ssl-lb'],
            'load-balancer-names': ['selected-listener'],
          },
          loadBalancerSelectionMetadata: {
            'selected-unavailable-lb': {
              key: 'load-balancer-names',
              names: ['selected-listener'],
            },
          },
          loadBalancers: ['selected-unavailable-lb'],
          viewState: {
            ...buildCommand().viewState,
            initialLoadBalancers: ['selected-unavailable-lb'],
            mode: 'clone',
          },
        });

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.loadBalancers).toEqual(['selected-listener']);
        expect(transformed.instanceMetadata).toEqual({
          'global-load-balancer-names': 'unselected-ssl-lb',
          'load-balancer-names': 'selected-listener',
        });
      });

      it('preserves unchanged legacy flat metadata without per-selection references', () => {
        const command = buildCommand({
          loadBalancerMetadata: {
            'global-load-balancer-names': ['legacy-global-listener'],
            'load-balancer-names': ['legacy-regional-listener'],
          },
          loadBalancers: ['legacy-lb'],
          viewState: { ...buildCommand().viewState, mode: 'clone' },
        });

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.loadBalancers).toEqual(['legacy-regional-listener']);
        expect(transformed.instanceMetadata).toEqual({
          'global-load-balancer-names': 'legacy-global-listener',
          'load-balancer-names': 'legacy-regional-listener',
        });
      });

      it('strips internal selection metadata from transformed commands', () => {
        const command = buildCommand({
          loadBalancerSelectionMetadata: {
            'legacy-lb': {
              key: 'load-balancer-names',
              names: ['legacy-regional-listener'],
            },
          },
          loadBalancerMetadata: {
            'load-balancer-names': ['legacy-regional-listener'],
          },
          loadBalancers: ['legacy-lb'],
          viewState: { ...buildCommand().viewState, mode: 'clone' },
        });

        const transformed = transformGceServerGroupCommand(command);

        expect(transformed.loadBalancerSelectionMetadata).toBeUndefined();
      });

      (['clone', 'createPipeline', 'editPipeline'] as const).forEach((mode) => {
        it(`strips internal load balancer attribution viewState from transformed ${mode} commands`, () => {
          const command = buildCommand({
            loadBalancerSelectionMetadata: {
              'legacy-lb': {
                key: 'load-balancer-names',
                names: ['legacy-regional-listener'],
              },
            },
            loadBalancers: ['legacy-lb'],
            viewState: {
              ...buildCommand().viewState,
              mode,
              dirty: { loadBalancers: ['legacy-lb'] },
              disableImageSelection: true,
              initialLoadBalancers: ['legacy-lb'],
              loadBalancerSelectionsChanged: true,
            },
          });

          const transformed = transformGceServerGroupCommand(command);

          expect(transformed.viewState.initialLoadBalancers).toBeUndefined();
          expect(transformed.viewState.loadBalancerSelectionsChanged).toBeUndefined();
          expect(transformed.viewState.mode).toBe(mode);
          expect(transformed.viewState.dirty).toEqual({ loadBalancers: ['legacy-lb'] });
          expect(transformed.viewState.disableImageSelection).toBe(true);
        });
      });
    });

    it('submits mixed regional HTTP, network, and global SSL/TCP attachments', () => {
      const command = buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'external-managed-lb': {
                listeners: [{ name: 'external-listener' }],
                loadBalancerType: 'EXTERNAL_MANAGED',
                name: 'external-managed-lb',
              },
              'global-tcp-lb': { loadBalancerType: 'TCP', name: 'global-tcp-lb' },
              'http-url-map': {
                listeners: [{ name: 'http-forwarding-rule' }],
                loadBalancerType: 'HTTP',
                name: 'http-url-map',
              },
              'regional-network-lb': {
                loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
                name: 'regional-network-lb',
              },
            },
          },
        },
        loadBalancers: ['external-managed-lb', 'regional-network-lb', 'global-tcp-lb', 'http-url-map'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      });

      const transformed = transformGceServerGroupCommand(command);

      expect(transformed.loadBalancers).toEqual(['external-listener', 'regional-network-lb', 'global-tcp-lb']);
      expect(transformed.instanceMetadata).toEqual({
        'global-load-balancer-names': 'global-tcp-lb,http-forwarding-rule',
        'load-balancer-names': 'external-listener,regional-network-lb',
      });
    });
  });

  it('maps EXTERNAL_MANAGED selections to regional listener metadata', () => {
    const transformed = transformGceServerGroupCommand(
      buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'external-managed-lb': {
                listeners: [{ name: 'external-listener' }],
                loadBalancerType: 'EXTERNAL_MANAGED',
                name: 'external-managed-lb',
              },
            },
          },
        },
        loadBalancers: ['external-managed-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      }),
    );

    expect(transformed.instanceMetadata).toEqual({
      'load-balancer-names': 'external-listener',
    });
  });

  it('maps REGIONAL_EXTERNAL_NETWORK selections to direct regional load balancer metadata', () => {
    const transformed = transformGceServerGroupCommand(
      buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'regional-network-lb': {
                loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
                name: 'regional-network-lb',
              },
            },
          },
        },
        loadBalancers: ['regional-network-lb'],
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      }),
    );

    expect(transformed.instanceMetadata).toEqual({
      'load-balancer-names': 'regional-network-lb',
    });
  });

  ['clone', 'editPipeline'].forEach((mode) => {
    it(`strips stale ignored loadBalancingPolicy from REGIONAL_EXTERNAL_NETWORK-only ${mode} submit payloads`, () => {
      const transformed = transformGceServerGroupCommand(
        buildCommand({
          backingData: {
            filtered: {
              loadBalancerIndex: {
                'regional-network-lb': {
                  loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
                  name: 'regional-network-lb',
                },
              },
            },
          },
          loadBalancers: ['regional-network-lb'],
          loadBalancingPolicy: {
            balancingMode: 'UTILIZATION',
            capacityScaler: 2,
            maxUtilization: 2,
            namedPorts: [{ name: '', port: 65536 }],
          },
          viewState: { ...buildCommand().viewState, mode },
        }),
      );

      expect(transformed.loadBalancingPolicy).toBeUndefined();
    });
  });

  it('preserves applicable loadBalancingPolicy for mixed EXTERNAL_MANAGED selections on submit', () => {
    const transformed = transformGceServerGroupCommand(
      buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'external-managed-lb': {
                listeners: [{ name: 'external-listener' }],
                loadBalancerType: 'EXTERNAL_MANAGED',
                name: 'external-managed-lb',
              },
              'regional-network-lb': {
                loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
                name: 'regional-network-lb',
              },
            },
          },
        },
        loadBalancers: ['external-managed-lb', 'regional-network-lb'],
        loadBalancingPolicy: {
          balancingMode: 'RATE',
          capacityScaler: 1,
          maxRatePerInstance: 100,
          namedPorts: [{ name: 'https', port: 443 }],
        },
        viewState: { ...buildCommand().viewState, mode: 'clone' },
      }),
    );

    expect(transformed.loadBalancingPolicy).toEqual({
      balancingMode: 'RATE',
      capacityScaler: 1,
      maxRatePerInstance: 100,
      namedPorts: [{ name: 'https', port: 443 }],
    });
  });

  it('rebuilds pipeline metadata from the selected regional load balancer and backend services', () => {
    const transformed = transformGceServerGroupCommand(
      buildCommand({
        backingData: {
          filtered: {
            loadBalancerIndex: {
              'regional-lb': { loadBalancerType: 'NETWORK', name: 'regional-lb' },
            },
          },
        },
        backendServices: { 'regional-lb': ['backend-a', 'backend-b'], removed: ['removed-backend'] },
        instanceMetadata: {
          'backend-service-names': 'persisted-backend',
          'global-load-balancer-names': 'persisted-global-rule',
        },
        loadBalancerMetadata: { 'global-load-balancer-names': ['persisted-global-rule'] },
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
    it(`returns a transformed command without executing infrastructure in ${mode} mode`, () => {
      const command = buildCommand({
        capacity: { desired: '${ parameters.desired }' } as any,
        minCpuPlatform: '(Automatic)',
        tags: [{ value: 'web' }, 'api'],
        viewState: { ...buildCommand().viewState, mode },
      });
      const props = buildProps(command);
      const modal = new GceCloneServerGroupModal(props) as any;
      const cloneServerGroup = jasmine.createSpy('cloneServerGroup');

      modal.submit(command);

      expect(props.closeModal).toHaveBeenCalledWith(
        jasmine.objectContaining({
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
      expect(cloneServerGroup).not.toHaveBeenCalled();
    });
  });

  ['create', 'clone'].forEach((mode) => {
    it(`transforms and submits ${mode} commands through TaskMonitor`, () => {
      const command = buildCommand({
        autoscalingPolicy: { maxNumReplicas: 6, minNumReplicas: 2 },
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
      });
      const props = buildProps(command);
      const modal = new GceCloneServerGroupModal(props) as any;
      const task = Promise.resolve({ id: 'task-id' });
      const cloneServerGroup = jasmine.createSpy('cloneServerGroup').and.returnValue(task as any);
      modal.context = { services: { serverGroupWriter: { cloneServerGroup } } };
      const monitorSubmit = spyOn(
        modal.state.taskMonitor,
        'submit',
      ).and.callFake((submitMethod: () => PromiseLike<any>) => submitMethod());

      modal.submit(command);

      expect(monitorSubmit).toHaveBeenCalled();
      expect(cloneServerGroup).toHaveBeenCalledWith(
        jasmine.objectContaining({
          autoscalingPolicy: { maxNumReplicas: 6, minNumReplicas: 2 },
          capacity: { desired: 3, max: 6, min: 2 },
          instanceMetadata: {
            owner: 'delivery',
            'global-load-balancer-names': 'global-forwarding-rule',
            'load-balancer-names': 'regional-forwarding-rule',
          },
          targetSize: 3,
        }),
        application,
      );
      const submitted = cloneServerGroup.calls.mostRecent().args[0];
      expect(submitted.loadBalancerMetadata).toBeUndefined();
      expect(submitted.securityGroups).toBeUndefined();
      expect(props.closeModal).not.toHaveBeenCalled();
    });
  });

  it('submits the immediate Formik command instead of stale component state', () => {
    const viewState = { ...buildCommand().viewState, mode: 'editPipeline' as const };
    const initialCommand = buildCommand({ stack: 'old', viewState });
    const formik = { values: buildCommand({ stack: 'edited', viewState }) } as any;
    const props = buildProps(initialCommand, buildAdapter());
    const wrapper = shallow(<GceCloneServerGroupModal {...props} />, { disableLifecycleMethods: true } as any);
    const modal = wrapper.instance() as any;
    const wizard = wrapper.find(WizardModal);
    wizard.prop('render')({ formik, nextIdx: () => 1, wizard: {} as any });

    wizard.prop('closeModal')();

    expect(props.closeModal).toHaveBeenCalledWith(jasmine.objectContaining({ stack: 'edited' }));
  });

  it('merges refreshed backing data and handlers into edits made while loading', async () => {
    const command = buildCommand({ stack: 'old', unknownReference: 'keep-me' });
    const request = deferred<IGceServerGroupCommand>();
    const adapter = buildAdapter();
    adapter.configureCommand.and.returnValue(request.promise);
    const wrapper = shallow(<GceCloneServerGroupModal {...buildProps(command, adapter)} />, {
      disableLifecycleMethods: true,
    } as any);
    const modal = wrapper.instance() as any;
    const formik: any = {
      setValues: jasmine
        .createSpy('setValues')
        .and.callFake((values: IGceServerGroupCommand) => (formik.values = values)),
      values: cloneDeep(command),
    };
    wrapper.find(WizardModal).prop('render')({ formik, nextIdx: () => 1, wizard: {} as any });

    const configure = modal.configureCommand();
    formik.values = { ...formik.values, stack: 'edited' };
    const regionChanged = jasmine.createSpy('regionChanged');
    request.resolve(
      buildCommand({
        backingData: { filtered: { regions: ['refreshed-region'] } },
        regionChanged,
        stack: 'old',
        unknownReference: 'keep-me',
      }),
    );
    await configure;

    expect(formik.values.stack).toBe('edited');
    expect(formik.values.unknownReference).toBe('keep-me');
    expect(formik.values.backingData.filtered.regions).toEqual(['refreshed-region']);
    expect(formik.values.regionChanged).toBe(regionChanged);
  });

  it('ignores stale backing refreshes', async () => {
    const first = deferred<IGceServerGroupCommand>();
    const second = deferred<IGceServerGroupCommand>();
    const adapter = buildAdapter();
    adapter.configureCommand.and.returnValues(first.promise, second.promise);
    const modal = new GceCloneServerGroupModal(buildProps(buildCommand(), adapter)) as any;
    spyOn(modal, 'setState').and.callFake((state: any) => (modal.state = { ...modal.state, ...state }));

    const firstConfigure = modal.configureCommand();
    const secondConfigure = modal.configureCommand();
    second.resolve(buildCommand({ backingData: { filtered: { regions: ['second'] } } }));
    await secondConfigure;
    first.resolve(buildCommand({ backingData: { filtered: { regions: ['first'] } } }));
    await firstConfigure;

    expect(modal.state.command.backingData.filtered.regions).toEqual(['second']);
  });

  it('does not update state or Formik after unmount', async () => {
    const request = deferred<IGceServerGroupCommand>();
    const adapter = buildAdapter();
    adapter.configureCommand.and.returnValue(request.promise);
    const modal = new GceCloneServerGroupModal(buildProps(buildCommand(), adapter)) as any;
    const setState = spyOn(modal, 'setState');
    const formik = { setValues: jasmine.createSpy('setValues'), values: buildCommand() };
    modal.formik = formik;

    const configure = modal.configureCommand();
    modal.componentWillUnmount();
    request.resolve(buildCommand({ backingData: { filtered: { regions: ['late'] } } }));
    await configure;

    expect(setState).not.toHaveBeenCalled();
    expect(formik.setValues).not.toHaveBeenCalled();
  });

  it('refreshes and navigates to the created server group after task completion', () => {
    const command = buildCommand({ credentials: 'gce-account', region: 'us-central1' });
    const props = buildProps(command);
    const modal = new GceCloneServerGroupModal(props) as any;
    modal.state.taskMonitor.task = {
      execution: {
        stages: [
          {
            context: { 'deploy.server.groups': { 'us-central1': 'fnord-main-api-v042' } },
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

    expect(application.serverGroups.refresh).toHaveBeenCalled();
    expect(application.serverGroups.onNextRefresh).toHaveBeenCalledWith(modal.onApplicationRefresh);
    expect(application.serverGroups.onNextRefresh.calls.first().invocationOrder).toBeLessThan(
      application.serverGroups.refresh.calls.first().invocationOrder,
    );
    application.serverGroups.onNextRefresh.calls.mostRecent().args[0]();
    expect(state.go).toHaveBeenCalledWith('.serverGroup', {
      accountId: 'gce-account',
      provider: 'gce',
      region: 'us-central1',
      serverGroup: 'fnord-main-api-v042',
    });
  });

  it('unsubscribes from the application refresh after the callback runs', () => {
    const unsubscribe = jasmine.createSpy('unsubscribe');
    application.serverGroups.onNextRefresh.and.returnValue(unsubscribe);
    const modal = new GceCloneServerGroupModal(buildProps(buildCommand())) as any;

    modal.onTaskComplete();
    expect(unsubscribe).not.toHaveBeenCalled();

    modal.onApplicationRefresh();
    expect(unsubscribe).toHaveBeenCalledTimes(1);
  });

  it('ignores a late application refresh after unmount', () => {
    const unsubscribe = jasmine.createSpy('unsubscribe');
    let refreshCallback: (() => void) | undefined;
    application.serverGroups.onNextRefresh.and.callFake((callback: () => void) => {
      refreshCallback = callback;
      return unsubscribe;
    });
    const props = buildProps(buildCommand({ credentials: 'gce-account', region: 'us-central1' }));
    const modal = new GceCloneServerGroupModal(props) as any;
    modal.state.taskMonitor.task = {
      execution: {
        stages: [
          {
            context: { 'deploy.server.groups': { 'us-central1': 'fnord-main-api-v042' } },
            type: 'cloneServerGroup',
          },
        ],
      },
    };

    modal.onTaskComplete();
    modal.componentWillUnmount();
    refreshCallback!();

    expect(unsubscribe).toHaveBeenCalledTimes(1);
    expect(modal.applicationRefreshUnsubscribe).toBeUndefined();
    expect(props.stateService.go).not.toHaveBeenCalled();
    expect(props.closeModal).not.toHaveBeenCalled();
    expect(props.dismissModal).not.toHaveBeenCalled();
  });

  it('unsubscribes from a pending application refresh before replacing it', () => {
    const firstUnsubscribe = jasmine.createSpy('firstUnsubscribe');
    const secondUnsubscribe = jasmine.createSpy('secondUnsubscribe');
    application.serverGroups.onNextRefresh.and.returnValues(firstUnsubscribe, secondUnsubscribe);
    const modal = new GceCloneServerGroupModal(buildProps(buildCommand())) as any;

    modal.onTaskComplete();
    modal.onTaskComplete();

    expect(firstUnsubscribe).toHaveBeenCalledTimes(1);
    expect(secondUnsubscribe).not.toHaveBeenCalled();
  });
});

function buildCommand(overrides: Partial<IGceServerGroupCommand> = {}): IGceServerGroupCommand {
  return {
    application: 'fnord',
    backingData: {
      accounts: ['gce-account'],
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
    viewState: {
      dirty: {},
      disableImageSelection: false,
      mode: 'create',
      useSimpleCapacity: true,
    },
    zone: 'us-central1-a',
    ...overrides,
  };
}

function buildProps(command: IGceServerGroupCommand, adapter?: IGceServerGroupWizardAdapter): any {
  return {
    adapter,
    application,
    closeModal: jasmine.createSpy('closeModal'),
    command,
    dismissModal: jasmine.createSpy('dismissModal'),
    router: {},
    stateParams: {},
    stateService: { go: jasmine.createSpy('go'), includes: () => false },
    title: 'Configure GCE server group',
  };
}

function buildAdapter(): any {
  return {
    applyCommandHandler: jasmine.createSpy('applyCommandHandler'),
    applyConfigurationRefresh: jasmine.createSpy('applyConfigurationRefresh'),
    applyConfigurationUpdate: jasmine.createSpy('applyConfigurationUpdate'),
    buildNewServerGroupCommand: jasmine.createSpy('buildNewServerGroupCommand'),
    configureCommand: jasmine.createSpy('configureCommand'),
  };
}

function buildInitializationHandlers(
  onCall: (handler: string, command: IGceServerGroupCommand) => void = () => undefined,
): Record<string, jasmine.Spy> {
  return [
    'credentialsChanged',
    'regionalChanged',
    'regionChanged',
    'networkChanged',
    'zoneChanged',
    'customInstanceChanged',
  ].reduce((handlers, handler) => {
    handlers[handler] = jasmine.createSpy(handler).and.callFake((command: IGceServerGroupCommand) => {
      onCall(handler, command);
      return { dirty: {} };
    });
    return handlers;
  }, {} as Record<string, jasmine.Spy>);
}

function deferred<T>(): { promise: Promise<T>; reject: (reason?: any) => void; resolve: (value: T) => void } {
  let reject: (reason?: any) => void;
  let resolve: (value: T) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    reject = promiseReject;
    resolve = promiseResolve;
  });
  return { promise, reject, resolve };
}

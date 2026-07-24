import { registerDefaultFixtures } from '../../support';

const loadBalancers = [
  {
    accounts: [
      {
        name: 'gce',
        regions: [
          {
            loadBalancers: [
              {
                account: 'gce',
                backendServices: [{ name: 'pipeline-backend', portName: 'http' }],
                loadBalancerType: 'HTTP',
                name: 'pipeline-listener',
                region: 'global',
                type: 'gce',
                urlMapName: 'pipeline-http-lb',
              },
            ],
            name: 'global',
          },
        ],
      },
    ],
    name: 'pipeline-http-lb',
  },
];

const applicationLoadBalancers = loadBalancers[0].accounts[0].regions[0].loadBalancers.map((loadBalancer) => ({
  ...loadBalancer,
  serverGroups: [],
}));

const wizardLoadBalancer = {
  account: 'gce',
  backendServices: [{ name: 'pipeline-backend', portName: 'http' }],
  listeners: [{ name: 'pipeline-listener' }],
  loadBalancerType: 'HTTP',
  name: 'pipeline-http-lb',
  region: 'global',
  type: 'gce',
  urlMapName: 'pipeline-http-lb',
};

const wizardLoadBalancers = [
  {
    accounts: [{ name: 'gce', regions: [{ loadBalancers: [wizardLoadBalancer], name: 'global' }] }],
    name: 'pipeline-http-lb',
  },
];

function copy(value) {
  return JSON.parse(JSON.stringify(value));
}

function loadBalancerMetadata(metadata = {}) {
  return ['global-load-balancer-names', 'load-balancer-names'].reduce((result, key) => {
    if (metadata[key]) {
      result[key] = String(metadata[key]).split(',').filter(Boolean);
    }
    return result;
  }, {});
}

function loadBalancerNames(metadata = {}) {
  return Object.values(loadBalancerMetadata(metadata)).flat();
}

function serverGroupFromCloneCommand(source, command, name) {
  const region = command.region;
  const zones = command.distributionPolicy?.zones?.length
    ? command.distributionPolicy.zones
    : command.availabilityZones?.[region] || [command.zone].filter(Boolean);
  const reservedMetadata = new Set(['backend-service-names', 'global-load-balancer-names', 'load-balancer-names']);
  const metadataItems = Object.entries(command.instanceMetadata || {})
    .filter(([key]) => !reservedMetadata.has(key))
    .map(([key, value]) => ({ key, value }));
  const sourceProperties = source.launchConfig.instanceTemplate.properties;
  const disks = (command.disks || []).map((disk, index) => {
    const sourceDisk = sourceProperties.disks[index] || sourceProperties.disks[0];
    return {
      ...sourceDisk,
      initializeParams: {
        ...sourceDisk?.initializeParams,
        diskSizeGb: disk.sizeGb,
        diskType: disk.type,
        sourceImage: disk.sourceImage || sourceDisk?.initializeParams?.sourceImage,
      },
    };
  });
  const capacity = copy(command.capacity);

  return {
    ...source,
    account: command.account,
    asg: {
      ...source.asg,
      'backend-service-names': command.instanceMetadata?.['backend-service-names'],
      'global-load-balancer-names': command.instanceMetadata?.['global-load-balancer-names'],
      desiredCapacity: capacity.desired,
      maxSize: capacity.max,
      minSize: capacity.min,
    },
    autoscalingPolicy: copy(command.autoscalingPolicy),
    capacity,
    distributionPolicy: copy(command.distributionPolicy),
    instanceTemplateTags: copy(command.tags || []),
    launchConfig: {
      ...source.launchConfig,
      imageId: command.image,
      instanceTemplate: {
        ...source.launchConfig.instanceTemplate,
        properties: {
          ...sourceProperties,
          disks,
          machineType: command.instanceType,
          metadata: { ...sourceProperties.metadata, items: metadataItems },
          tags: { items: copy(command.tags || []) },
        },
      },
      instanceType: command.instanceType,
    },
    loadBalancingPolicy: copy(command.loadBalancingPolicy),
    loadBalancers: loadBalancerNames(command.instanceMetadata),
    moniker: {
      app: command.application,
      cluster: [command.application, command.stack, command.freeFormDetails].filter(Boolean).join('-'),
      detail: command.freeFormDetails,
      sequence: 1,
      stack: command.stack,
    },
    name,
    region,
    regional: command.regional,
    selectZones: command.selectZones,
    zone: command.regional ? undefined : command.zone,
    zones,
  };
}

function createInstanceTypeService(core) {
  const InstanceTypeService = core.CloudProviderRegistry.getValue('gce', 'instance.instanceTypeService');
  const delegate = new InstanceTypeService(core.AngularServices.$q);
  const findInstanceType = async (instanceType) => {
    const categories = await delegate.getCategories('gce');
    for (const category of categories) {
      for (const family of category.families) {
        const details = family.instanceTypes.find((candidate) => candidate.name === instanceType);
        if (details) {
          return { category, details };
        }
      }
    }
    return {};
  };

  return {
    ...delegate,
    getCategoryForInstanceType: async (_provider, instanceType) => {
      const { category } = await findInstanceType(instanceType);
      return category?.type || 'custom';
    },
    getInstanceTypeDetails: async (_provider, instanceType) => {
      const { details } = await findInstanceType(instanceType);
      return (
        details || {
          name: instanceType,
          storage: {
            count: 1,
            defaultSettings: { disks: [{ sizeGb: 10, type: 'pd-standard' }] },
            localSSDSupported: false,
            size: 10,
          },
        }
      );
    },
  };
}

function createCommandBuilder(core, instanceTypeService) {
  const CommandBuilder = core.CloudProviderRegistry.getValue('gce', 'serverGroup.commandBuilder');
  const delegate = new CommandBuilder(core.AngularServices.$q);

  const buildServerGroupCommandFromExisting = async (application, serverGroup, mode = 'clone') => {
    const source = copy(serverGroup);
    const disks = source.launchConfig?.instanceTemplate?.properties?.disks || [];
    source.launchConfig.instanceTemplate.properties.disks = [];
    const command = await delegate.buildServerGroupCommandFromExisting(application, source, mode);
    command.disks = disks.map((disk) => ({
      sizeGb: disk.initializeParams?.diskSizeGb || 10,
      sourceImage: disk.initializeParams?.sourceImage?.split('/').pop(),
      type: disk.initializeParams?.diskType || 'pd-standard',
    }));
    command.viewState.instanceTypeDetails = await instanceTypeService.getInstanceTypeDetails(
      'gce',
      command.instanceType,
    );
    command.image = source.launchConfig.imageId;
    return command;
  };

  const buildServerGroupCommandFromPipeline = async (application, cluster, currentStage, pipeline) => {
    const region = cluster.region || Object.keys(cluster.availabilityZones || {})[0];
    const zone = cluster.zone || cluster.availabilityZones?.[region]?.[0];
    const base = await delegate.buildNewServerGroupCommand(application, {
      account: cluster.account,
      region,
      zone,
    });
    const metadata = { ...(cluster.instanceMetadata || {}) };
    const customMetadata = { ...metadata };
    delete customMetadata['global-load-balancer-names'];
    delete customMetadata['load-balancer-names'];
    delete customMetadata['backend-service-names'];
    const instanceProfile = await instanceTypeService.getCategoryForInstanceType('gce', cluster.instanceType);

    return {
      ...base,
      ...copy(cluster),
      backendServiceMetadata: metadata['backend-service-names']
        ? String(metadata['backend-service-names']).split(',').filter(Boolean)
        : cluster.backendServiceMetadata || [],
      credentials: cluster.account,
      distributionPolicy: copy(cluster.distributionPolicy || { zones: [] }),
      enableTraffic: !cluster.disableTraffic,
      instanceMetadata: customMetadata,
      loadBalancerMetadata: loadBalancerMetadata(metadata),
      loadBalancers: cluster.loadBalancers?.length ? cluster.loadBalancers : loadBalancerNames(metadata),
      minCpuPlatform: cluster.minCpuPlatform || '(Automatic)',
      region,
      regional: Boolean(cluster.regional),
      tags: (cluster.tags || []).map((tag) => (typeof tag === 'string' ? { value: tag } : tag)),
      viewState: {
        ...base.viewState,
        disableImageSelection: true,
        expectedArtifacts: [],
        instanceProfile,
        mode: 'editPipeline',
        pipeline,
        showImageSourceSelector: true,
        stage: currentStage,
        submitButtonLabel: 'Done',
        templatingEnabled: true,
        useSimpleCapacity: !cluster.autoscalingPolicy,
      },
      zone,
    };
  };

  return {
    buildNewServerGroupCommand: (application, defaults) => delegate.buildNewServerGroupCommand(application, defaults),
    buildNewServerGroupCommandForPipeline: (currentStage, pipeline) =>
      delegate.buildNewServerGroupCommandForPipeline(currentStage, pipeline),
    buildServerGroupCommandFromExisting,
    buildServerGroupCommandFromPipeline,
  };
}

function createWizardAdapter(core, instanceTypeService, securityGroupReader, win, lifecycle) {
  const zones = ['us-central1-a', 'us-central1-b', 'us-central1-c', 'us-central1-f'];
  const backendServiceNames = (value) =>
    Array.isArray(value)
      ? value
      : String(value || '')
          .split(',')
          .filter(Boolean);
  const loadImages = (account) =>
    win
      .fetch(`/images/find?provider=gce&account=${account}`)
      .then((response) => response.json())
      .then((images) => images.filter((image) => image.account === account));
  const cloneForUpdate = (command) => ({
    ...command,
    backingData: {
      ...command.backingData,
      filtered: { ...command.backingData?.filtered },
    },
    distributionPolicy: {
      ...(command.distributionPolicy || {}),
      zones: [...(command.distributionPolicy?.zones || [])],
    },
    viewState: { ...command.viewState, dirty: { ...(command.viewState?.dirty || {}) } },
  });
  const update = (command, result = { dirty: {} }) => {
    command.viewState.dirty = { ...command.viewState.dirty, ...result.dirty };
    command.processCommandUpdateResult?.(result);
    return { command, result };
  };
  const configureImages = (command) => {
    const result = { dirty: {} };
    command.backingData.filtered.images = command.backingData.allImages;
    if (command.credentials !== command.viewState.lastImageAccount) {
      command.viewState.lastImageAccount = command.credentials;
      if (command.image && !command.backingData.allImages.some((image) => image.imageName === command.image)) {
        command.image = null;
        result.dirty.imageName = true;
      }
    }
    return result;
  };
  const configureLoadBalancerOptions = (command) => {
    const result = { dirty: {} };
    const available = [wizardLoadBalancer];
    const index = Object.fromEntries(available.map((loadBalancer) => [loadBalancer.name, loadBalancer]));
    const current = command.loadBalancers || [];
    const normalized = current
      .map((name) => {
        const loadBalancer = available.find(
          (loadBalancer) =>
            loadBalancer.name === name || loadBalancer.listeners?.some((listener) => listener.name === name),
        );
        if (loadBalancer && loadBalancer.name !== name) {
          lifecycle.push(`loadBalancer:${name}->${loadBalancer.name}`);
        }
        return loadBalancer;
      })
      .filter(Boolean)
      .map((loadBalancer) => loadBalancer.name);
    const removed = current.filter((name) => !normalized.includes(name) && !index[name]);
    command.loadBalancers = Array.from(new Set(normalized));
    command.backingData.filtered.loadBalancerIndex = index;
    command.backingData.filtered.loadBalancers = available.map((loadBalancer) => loadBalancer.name);
    const metadata = backendServiceNames(command.backendServiceMetadata);
    command.backendServiceMetadata = metadata;
    command.backendServices = Object.fromEntries(
      command.loadBalancers.map((name) => {
        const availableBackends = (index[name].backendServices || []).map((backend) => backend.name || backend);
        return [name, availableBackends.filter((backend) => metadata.includes(backend))];
      }),
    );
    if (removed.length) {
      result.dirty.loadBalancers = removed;
    }
    return result;
  };
  const reconcileInstanceTypeStorage = async (command) => {
    if (!command.instanceType) {
      return { dirty: {} };
    }
    const details = await instanceTypeService.getInstanceTypeDetails('gce', command.instanceType);
    const previous = command.viewState.lastReconciledInstanceType;
    command.viewState.instanceTypeDetails = details;
    command.viewState.lastReconciledInstanceType = command.instanceType;
    if (previous !== command.instanceType && details.storage?.defaultSettings?.disks) {
      command.disks = copy(details.storage.defaultSettings.disks);
      delete command.viewState.overriddenStorageDescription;
      lifecycle.push(`storage:${command.instanceType}`);
    }
    return { dirty: {} };
  };
  const configureAccountOptions = (command) => {
    command.backingData.filtered.regions = ['us-central1'];
    command.backingData.filtered.networks = command.backingData.networks
      .filter((network) => network.account === command.credentials)
      .map((network) => network.name);
    command.backingData.filtered.subnets = command.backingData.subnets
      .filter((subnet) => subnet.account === command.credentials && subnet.region === command.region)
      .map((subnet) => subnet.name);
    command.backingData.filtered.zones = zones;
  };
  const configureSubnets = (command) => {
    const result = { dirty: {} };
    command.backingData.filtered.subnets = command.backingData.subnets
      .filter(
        (subnet) =>
          subnet.account === command.credentials &&
          subnet.network === command.network &&
          subnet.region === command.region,
      )
      .map((subnet) => subnet.name);
    if (command.subnet && !command.backingData.filtered.subnets.includes(command.subnet)) {
      command.subnet = '';
      result.dirty.subnet = true;
    }
    return result;
  };
  const configureZones = (command) => {
    const result = { dirty: {} };
    command.backingData.filtered.zones = zones;
    if (command.zone && !zones.includes(command.zone)) {
      command.zone = undefined;
      if (!command.regional) {
        result.dirty.zone = true;
      }
    }
    return result;
  };
  const runHandler = (command, handler, phase) => {
    lifecycle.push(`${phase}:${handler}`);
    const result = { dirty: {} };
    if (handler === 'credentialsChanged') {
      configureAccountOptions(command);
      Object.assign(result.dirty, runHandler(command, 'regionChanged', phase).dirty);
      Object.assign(result.dirty, runHandler(command, 'networkChanged', phase).dirty);
    } else if (handler === 'regionalChanged') {
      if (command.regional) {
        command.zone = null;
        command.distributionPolicy.targetShape ||= 'EVEN';
      } else if (!command.zone) {
        command.zone = zones[0];
      }
    } else if (handler === 'regionChanged') {
      configureAccountOptions(command);
      Object.assign(
        result.dirty,
        configureSubnets(command).dirty,
        configureZones(command).dirty,
        configureLoadBalancerOptions(command).dirty,
        configureImages(command).dirty,
      );
    } else if (handler === 'networkChanged') {
      Object.assign(result.dirty, configureSubnets(command).dirty);
    } else if (handler === 'zoneChanged' && command.zone === undefined && !command.regional) {
      result.dirty.zone = true;
    }
    Object.assign(command.viewState.dirty, result.dirty);
    return result;
  };
  const attachEventHandlers = (command) => {
    [
      'credentialsChanged',
      'regionalChanged',
      'regionChanged',
      'networkChanged',
      'zoneChanged',
      'customInstanceChanged',
    ].forEach((handler) => {
      command[handler] = (nextCommand) => runHandler(nextCommand, handler, 'initialize');
    });
  };
  const configuredCommand = async (command) => {
    const [accounts, images, networks, securityGroups, subnets, categories] = await Promise.all([
      core.AccountService.listAccounts('gce'),
      loadImages(command.credentials),
      win.fetch('/networks/gce').then((response) => response.json()),
      securityGroupReader.getAllSecurityGroups(),
      win.fetch('/subnets/gce').then((response) => response.json()),
      instanceTypeService.getCategories('gce'),
    ]);
    const accountOptions = accounts.map((account) => (typeof account === 'string' ? { name: account } : account));
    const accountNames = accountOptions.map((account) => account.name);
    if (!accountNames.includes(command.credentials)) {
      accountOptions.push({ name: command.credentials });
    }
    const instanceTypes = categories
      .flatMap((category) => category.families)
      .flatMap((family) => family.instanceTypes)
      .map((instanceType) => instanceType.name)
      .filter((name) => !name.endsWith('buildCustom'));
    const next = {
      ...command,
      backingData: {
        ...(command.backingData || {}),
        accounts: accountOptions,
        allImages: images,
        authScopes: ['cloud.useraccounts.readonly', 'devstorage.read_only', 'logging.write', 'monitoring.write'],
        customInstanceTypes: {
          instanceFamilyList: ['N1', 'E2', 'N2', 'N2D'],
          memoryList: [1, 1.25, 4.5, 5.75, 6.5],
          vCpuList: [1, 2, 4, 8, 16, 32, 64, 96],
        },
        distributionPolicyTargetShapes: ['ANY', 'EVEN'],
        filtered: {
          cpuPlatforms: ['(Automatic)'],
          healthChecks: [],
          images,
          instanceTypes,
          loadBalancerIndex: {},
          loadBalancers: [],
          networks: [],
          regions: [],
          subnets: [],
          zones,
        },
        loadBalancers: wizardLoadBalancers.map((provider) => ({
          ...provider,
          accounts: accountOptions.map((account) => ({
            name: account.name,
            regions: copy(provider.accounts[0].regions),
          })),
        })),
        networks,
        persistentDiskTypes: ['pd-standard', 'pd-ssd', 'hyperdisk-balanced'],
        securityGroups,
        subnets,
      },
      viewState: {
        ...command.viewState,
        dirty: { ...(command.viewState?.dirty || {}) },
        lastReconciledInstanceType: command.instanceType,
      },
    };
    configureAccountOptions(next);
    configureImages(next);
    configureLoadBalancerOptions(next);
    attachEventHandlers(next);
    return next;
  };

  return {
    applyCommandHandler: async (command, handler) => {
      const next = cloneForUpdate(command);
      if (handler === 'credentialsChanged') {
        lifecycle.push(`images:${next.credentials}`);
        next.backingData.allImages = await loadImages(next.credentials);
      }
      const result = runHandler(next, handler, 'update');
      if (['regionChanged', 'zoneChanged', 'selectZonesChanged', 'customInstanceChanged'].includes(handler)) {
        Object.assign(result.dirty, (await reconcileInstanceTypeStorage(next)).dirty);
      }
      return update(next, result);
    },
    applyConfigurationRefresh: async (command, method) => {
      const next = cloneForUpdate(command);
      if (method === 'refreshInstanceTypes') {
        await reconcileInstanceTypeStorage(next);
      } else if (method === 'refreshLoadBalancers') {
        configureLoadBalancerOptions(next);
      } else if (method === 'refreshSecurityGroups') {
        next.backingData.securityGroups = await securityGroupReader.getAllSecurityGroups();
      } else if (method === 'refreshHealthChecks') {
        next.backingData.filtered.healthChecks = [];
      }
      return update(next);
    },
    applyConfigurationUpdate: async (command, method) => {
      const next = cloneForUpdate(command);
      const result =
        method === 'configureLoadBalancerOptions'
          ? configureLoadBalancerOptions(next)
          : method === 'configureImages'
          ? configureImages(next)
          : method === 'configureInstanceTypes'
          ? await reconcileInstanceTypeStorage(next)
          : method === 'configureSubnets'
          ? configureSubnets(next)
          : method === 'configureZones'
          ? configureZones(next)
          : { dirty: {} };
      return update(next, result);
    },
    configureCommand: (_application, command) => configuredCommand(command),
  };
}

function installGceTestHarness() {
  cy.window().then((win) => {
    const core = win.spinnaker?.plugins?.sharedLibraries?._spinnaker_core;
    expect(core, 'shared @spinnaker/core library').to.exist;

    const securityGroupReader = {
      getAllSecurityGroups: () => core.REST('/securityGroups').get(),
    };
    const instanceTypeService = createInstanceTypeService(core);
    const commandBuilder = createCommandBuilder(core, instanceTypeService);
    const lifecycle = [];
    const adapter = {
      ...createWizardAdapter(core, instanceTypeService, securityGroupReader, win, lifecycle),
      ...commandBuilder,
    };
    const facade = core.ServerGroupCommandBuilderService.prototype;
    facade.buildNewServerGroupCommand = (application, _provider, defaults) =>
      commandBuilder.buildNewServerGroupCommand(application, defaults);
    facade.buildNewServerGroupCommandForPipeline = (_provider, stage, pipeline) =>
      commandBuilder.buildNewServerGroupCommandForPipeline(stage, pipeline);
    facade.buildServerGroupCommandFromExisting = commandBuilder.buildServerGroupCommandFromExisting;
    facade.buildServerGroupCommandFromPipeline = commandBuilder.buildServerGroupCommandFromPipeline;

    const CloneServerGroupModal = core.CloudProviderRegistry.getValue('gce', 'serverGroup.CloneServerGroupModal');
    const showModal = CloneServerGroupModal.show.bind(CloneServerGroupModal);
    const runtime = core.createDeckRuntime(core.getDirectRouter());
    const showModalWithAdapter = (props, runtimeServices = runtime.services) =>
      showModal({ ...props, adapter }, runtimeServices);
    core.CloudProviderRegistry.overrideValue('gce', 'serverGroup.CloneServerGroupModal.show', showModalWithAdapter);
    win.__gceFunctionalHarness = {
      adapter,
      commandBuilder,
      core,
      lifecycle,
      runtime,
      showModal: showModalWithAdapter,
    };
  });
}

function resolvedApplication(harness) {
  const globals = harness.core.getDirectRouter().globals;
  const transition = globals.transition || globals.successfulTransitions?.peekTail();
  return transition.injector().get('app');
}

function openCreateServerGroupWizard() {
  cy.window().then((win) => {
    const harness = win.__gceFunctionalHarness;
    const application = resolvedApplication(harness);
    return harness.core.ProviderSelectionService.selectProvider(
      application,
      'serverGroup',
      (_app, _account, provider) => Boolean(provider.serverGroup?.CloneServerGroupModal),
    ).then((provider) =>
      harness.commandBuilder.buildNewServerGroupCommand(application).then((command) => {
        expect(provider).to.equal('gce');
        harness.showModal({ application, command, isNew: true, title: 'Create New Server Group' });
      }),
    );
  });
}

function openCloneServerGroupWizard(account = 'compute-engine', serverGroup = 'compute-v000') {
  cy.window().then((win) => {
    const harness = win.__gceFunctionalHarness;
    const application = resolvedApplication(harness);
    return win
      .fetch(`/applications/compute/serverGroups/${account}/us-central1/${serverGroup}?includeDetails=false`)
      .then((response) => response.json())
      .then((details) =>
        harness.commandBuilder.buildServerGroupCommandFromExisting(application, {
          ...details,
          account,
          type: 'gce',
        }),
      )
      .then((command) => {
        harness.showModal({ application, command, title: `Clone ${serverGroup}` });
      });
  });
}

function openWizardPage(label) {
  cy.contains('.wizard-navigation a', label).click();
}

function submitWizard(label) {
  cy.contains('.modal-footer button', label).click();
}

function visitClassicClusters() {
  cy.visit('#/applications/compute/clusters', {
    onBeforeLoad: (window) => {
      let feature;
      const settings = {};
      Object.defineProperty(settings, 'feature', {
        configurable: true,
        get: () => feature,
        set: (value) => {
          feature = { ...value, statusUI: false };
        },
      });
      window.spinnakerSettings = settings;
    },
  });
}

function openDeployStage() {
  cy.contains('.pipeline-config-graph .label-body.node', 'Deploy').click({ force: true });
}

describe('google: Server Group Wizard', () => {
  beforeEach(() => {
    registerDefaultFixtures();
    cy.intercept('**/auth/user', {
      accountNonExpired: true,
      accountNonLocked: true,
      allowedAccounts: ['compute-engine', 'gce'],
      authorities: [],
      credentialsNonExpired: true,
      email: 'functional@example.com',
      enabled: true,
      roles: [],
      username: 'functional',
    });
    cy.intercept('/credentials?expand=true', {
      fixture: 'google/accelerator_zones/credentials.json',
    });
    cy.intercept('/images/find?*', {
      fixture: 'google/shared/images.json',
    }).as('images');
    cy.intercept('/applications/compute/serverGroups', {
      fixture: 'google/clone/serverGroups.json',
    });
    cy.intercept('/applications/compute/serverGroups/**/compute-v000?includeDetails=false', {
      fixture: 'google/clone/serverGroup.compute-v000.json',
    });
    cy.intercept('/applications/compute/pipelineConfigs', {
      fixture: 'google/pipelines_list/pipelineConfigs.json',
    });
    cy.intercept('/applications/compute/pipelines?**', {
      fixture: 'google/pipelines_list/pipelines.json',
    });
    cy.intercept('/applications/compute/loadBalancers', applicationLoadBalancers);
    cy.intercept('/loadBalancers?provider=gce', loadBalancers).as('loadBalancers');
  });

  it('provides custom CPU and memory options when cloning', () => {
    visitClassicClusters();
    installGceTestHarness();
    cy.get('.sub-group:contains("compute-engine")').find('.server-group:contains("v000")').click({ force: true });
    cy.contains('h3', 'compute-v000').should('be.visible');
    openCloneServerGroupWizard();

    openWizardPage('Instance Type');
    cy.get('#gce-machine-type-custom').click({ force: true });

    cy.get('#gce-custom-cpu option').then((options) => {
      const values = Array.from(options).map((option) => option.textContent);
      expect(values).to.include('1');
      expect(values).to.include('4');
      expect(values).to.include('16');
      expect(values).to.include('32');
      expect(values).to.include('64');
      expect(values).to.include('96');
    });
    cy.get('#gce-custom-memory option').then((options) => {
      const values = Array.from(options).map((option) => option.textContent);
      expect(values).to.include('1');
      expect(values).to.include('1.25');
      expect(values).to.include('4.5');
      expect(values).to.include('5.75');
      expect(values).to.include('6.5');
    });
  });

  it('creates a server group from the native wizard command', () => {
    cy.intercept('POST', '/tasks', (request) => {
      const command = request.body.job[0];
      expect(command.type).to.equal('createServerGroup');
      expect(command.account).to.equal('compute-engine');
      expect(command.stack).to.equal('functional');
      expect(command.freeFormDetails).to.equal('create');
      expect(command.image).to.equal('centos-7-v20180911');
      expect(command.instanceType).to.equal('n1-standard-1');
      expect(command.targetSize).to.equal(2);
      expect(command.capacity.desired).to.equal(2);
      expect(command.capacity.max).to.equal(2);
      expect(command.capacity.min).to.equal(2);
      request.reply({ ref: '/tasks/gce-create-task' });
    }).as('createServerGroup');
    cy.intercept('/tasks/gce-create-task', {
      application: 'compute',
      execution: { stages: [] },
      id: 'gce-create-task',
      name: 'Create server group',
      status: 'SUCCEEDED',
      variables: [],
    });

    visitClassicClusters();
    installGceTestHarness();
    cy.contains('.server-group', 'v000').should('be.visible');
    openCreateServerGroupWizard();

    cy.wait('@images').its('request.query.account').should('equal', 'gce');
    cy.get('select[aria-label="Account"]').select('compute-engine');
    cy.wait('@images').its('request.query.account').should('equal', 'compute-engine');
    cy.get('select[aria-label="Image"] option[value="centos-7-v20180911"]').should('have.length', 1);
    cy.window()
      .its('__gceFunctionalHarness.lifecycle')
      .should((lifecycle) => {
        expect(lifecycle.filter((entry) => entry.startsWith('initialize:'))).to.deep.equal([
          'initialize:credentialsChanged',
          'initialize:regionChanged',
          'initialize:networkChanged',
          'initialize:regionalChanged',
          'initialize:regionChanged',
          'initialize:networkChanged',
          'initialize:zoneChanged',
          'initialize:customInstanceChanged',
        ]);
        const accountChange = lifecycle.indexOf('images:compute-engine');
        expect(lifecycle.slice(accountChange, accountChange + 4)).to.deep.equal([
          'images:compute-engine',
          'update:credentialsChanged',
          'update:regionChanged',
          'update:networkChanged',
        ]);
      });
    cy.get('input[aria-label="Stack"]').type('functional');
    cy.get('input[aria-label="Detail"]').type('create');
    cy.get('select[aria-label="Image"]').select('centos-7-v20180911');
    cy.get('select[aria-label="Machine type"]').select('n1-standard-1');
    cy.get('input[aria-label="Desired capacity"]').type('{selectall}2');
    submitWizard('Done');

    cy.wait('@createServerGroup');
  });

  it('submits and reopens a clone without dropping persisted configuration', () => {
    const clonedServerGroupName = 'compute-clone-roundtrip-v001';
    let submittedCloneCommand;
    cy.fixture('google/clone/serverGroup.compute-v000.json').then((serverGroup) => {
      cy.fixture('google/clone/serverGroups.json').then((serverGroups) => {
        cy.intercept('/applications/compute/serverGroups', (request) => {
          const clonedServerGroup = submittedCloneCommand
            ? serverGroupFromCloneCommand(serverGroup, submittedCloneCommand, clonedServerGroupName)
            : null;
          request.reply(clonedServerGroup ? [...serverGroups, clonedServerGroup] : serverGroups);
        }).as('serverGroups');
      });
      cy.intercept(
        `/applications/compute/serverGroups/compute-engine/us-central1/${clonedServerGroupName}?includeDetails=false`,
        (request) => {
          expect(submittedCloneCommand, 'captured clone task command').to.exist;
          request.reply(serverGroupFromCloneCommand(serverGroup, submittedCloneCommand, clonedServerGroupName));
        },
      );
    });
    cy.intercept('POST', '/tasks', (request) => {
      const command = request.body.job[0];
      expect(command).to.include({
        freeFormDetails: 'roundtrip',
        instanceType: 'n1-standard-1',
        regional: true,
        selectZones: true,
        stack: 'clone',
        type: 'cloneServerGroup',
      });
      expect(command.distributionPolicy.zones).to.deep.equal(['us-central1-a', 'us-central1-b']);
      expect(command.autoscalingPolicy).to.deep.include({
        coolDownPeriodSec: 75,
        maxNumReplicas: 4,
        minNumReplicas: 1,
      });
      expect(command.autoscalingPolicy.cpuUtilization.utilizationTarget).to.equal(0.7);
      expect(command.loadBalancingPolicy).to.deep.equal({
        balancingMode: 'UTILIZATION',
        capacityScaler: 1,
        maxUtilization: 0.8,
        namedPorts: [{ name: 'http', port: 80 }],
      });
      expect(command.loadBalancers).to.include('pipeline-http-lb');
      expect(command.instanceMetadata).to.include({
        'backend-service-names': 'pipeline-backend',
        'clone-round-trip': 'submitted-value',
        'global-load-balancer-names': 'pipeline-listener',
      });
      expect(command.disks[0]).to.include({ sizeGb: 20, type: 'pd-ssd' });
      expect(command.tags).to.include('clone-round-trip-tag');
      submittedCloneCommand = copy(command);
      request.reply({ ref: '/tasks/gce-clone-task' });
    }).as('cloneServerGroup');
    cy.intercept('/tasks/gce-clone-task', {
      application: 'compute',
      execution: {
        stages: [
          {
            context: { 'deploy.server.groups': { 'us-central1': clonedServerGroupName } },
            type: 'cloneServerGroup',
          },
        ],
      },
      id: 'gce-clone-task',
      name: 'Clone server group',
      status: 'SUCCEEDED',
      variables: [],
    });

    visitClassicClusters();
    installGceTestHarness();
    cy.get('.sub-group:contains("compute-engine")').find('.server-group:contains("v000")').click({ force: true });
    cy.contains('h3', 'compute-v000').should('be.visible');
    openCloneServerGroupWizard();
    cy.get('input[aria-label="Stack"]').type('clone');
    cy.get('input[aria-label="Detail"]').type('roundtrip');
    openWizardPage('Instance Type');
    cy.get('select[aria-label="Machine type"]').select('n1-standard-1');
    openWizardPage('Basic Settings');
    cy.get('select[aria-label="Location mode"]').select('regional');
    openWizardPage('Capacity/Distribution');
    cy.get('input[aria-label="Desired capacity"]').type('{selectall}2');
    cy.get('input[aria-label="Explicit zone distribution"]').check({ force: true });
    cy.get('input[aria-label="Zone us-central1-a"]').check({ force: true });
    cy.get('input[aria-label="Zone us-central1-b"]').check({ force: true });
    openWizardPage('Load Balancers');
    cy.get('select[aria-label="Load balancers"]').select(['pipeline-http-lb']);
    cy.get('select[aria-label="Backend services for pipeline-http-lb"]').select(['pipeline-backend']);
    cy.get('select[aria-label="Balancing mode"]').should('have.value', 'UTILIZATION');
    cy.get('input[aria-label="Capacity scaler"]').should('have.value', '100');
    cy.get('input[aria-label="Max utilization"]').should('have.value', '80');
    openWizardPage('Policies');
    cy.get('[data-testid="enable-autoscaling"]').check({ force: true });
    cy.get('[data-testid="minimum-replicas"]').type('{selectall}1');
    cy.get('[data-testid="maximum-replicas"]').type('{selectall}4');
    cy.get('[data-testid="cooldown"]').type('{selectall}75');
    cy.get('[data-testid="cpu-target"]').type('{selectall}70');
    openWizardPage('Advanced Settings');
    cy.contains('.MapEditor', 'Custom Metadata').within(() => {
      cy.contains('button', 'Add New Metadata').click();
      cy.get('input[aria-label="Metadata key"]').last().type('clone-round-trip');
      cy.get('input[aria-label="Metadata value"]').last().type('submitted-value');
    });
    cy.get('input[aria-label="Disk size 1"]').type('{selectall}20');
    cy.get('[data-testid="add-network-tag"]').click();
    cy.get('input[aria-label="Network tag 1"]').type('clone-round-trip-tag');
    submitWizard('Done');
    cy.wait('@cloneServerGroup');
    cy.url().should('include', clonedServerGroupName);
    cy.contains('h3', clonedServerGroupName).should('be.visible');
    cy.contains('.modal-footer button', 'Close').click();
    cy.get('.modal-dialog').should('not.exist');

    openCloneServerGroupWizard('compute-engine', clonedServerGroupName);
    cy.get('input[aria-label="Stack"]').should('have.value', 'clone');
    cy.get('input[aria-label="Detail"]').should('have.value', 'roundtrip');
    cy.get('select[aria-label="Location mode"]').should('have.value', 'regional');
    openWizardPage('Instance Type');
    cy.get('select[aria-label="Machine type"]').should('have.value', 'n1-standard-1');
    openWizardPage('Capacity/Distribution');
    cy.get('input[aria-label="Desired capacity"]').should('have.value', '2');
    cy.get('input[aria-label="Regional server group"]').should('be.checked');
    cy.get('input[aria-label="Explicit zone distribution"]').should('be.checked');
    cy.get('input[aria-label="Zone us-central1-a"]').should('be.checked');
    cy.get('input[aria-label="Zone us-central1-b"]').should('be.checked');
    openWizardPage('Load Balancers');
    cy.get('select[aria-label="Load balancers"] option:checked').should('have.value', 'pipeline-http-lb');
    cy.get('select[aria-label="Backend services for pipeline-http-lb"] option:checked').should(
      'have.value',
      'pipeline-backend',
    );
    cy.get('select[aria-label="Balancing mode"]').should('have.value', 'UTILIZATION');
    cy.get('input[aria-label="Capacity scaler"]').should('have.value', '100');
    openWizardPage('Policies');
    cy.get('[data-testid="enable-autoscaling"]').should('be.checked');
    cy.get('[data-testid="minimum-replicas"]').should('have.value', '1');
    cy.get('[data-testid="maximum-replicas"]').should('have.value', '4');
    cy.get('[data-testid="cooldown"]').should('have.value', '75');
    cy.get('[data-testid="cpu-target"]').should('have.value', '70');
    openWizardPage('Advanced Settings');
    cy.contains('.MapEditor', 'Custom Metadata').within(() => {
      cy.get('input[aria-label="Metadata key"]').should('have.value', 'clone-round-trip');
      cy.get('input[aria-label="Metadata value"]').should('have.value', 'submitted-value');
    });
    cy.get('select[aria-label="Disk type 1"]').should('have.value', 'pd-ssd');
    cy.get('input[aria-label="Disk size 1"]').should('have.value', '20');
    cy.get('input[aria-label="Network tag 1"]').should('have.value', 'clone-round-trip-tag');
    cy.window()
      .its('__gceFunctionalHarness.lifecycle')
      .should((lifecycle) => {
        expect(lifecycle).to.include('storage:n1-standard-1');
        expect(lifecycle).to.include('loadBalancer:pipeline-listener->pipeline-http-lb');
        const storageReconciliation = lifecycle.indexOf('storage:n1-standard-1');
        expect(lifecycle[storageReconciliation - 1]).to.equal('update:zoneChanged');
      });
  });

  it('round-trips regional, policy, load balancer, and advanced settings in a pipeline-created deployment', () => {
    cy.visit('#/applications/compute/executions/configure/030b5d92-f06c-474a-9d8d-d1d951529140');
    installGceTestHarness();
    openDeployStage();
    cy.get('[data-test-id="Deploy.addServerGroup"]').click();

    cy.get('input[aria-label="Stack"]').type('pipeline');
    cy.get('input[aria-label="Detail"]').type('created');
    cy.get('select[aria-label="Region"]').select('us-central1');
    cy.get('select[aria-label="Location mode"]').select('regional');
    openWizardPage('Instance Type');
    cy.get('input[aria-label="Machine type"]').type('{selectall}n1-standard-1');

    openWizardPage('Capacity/Distribution');
    cy.get('input[aria-label="Desired capacity"]').type('{selectall}3');
    cy.get('input[aria-label="Regional server group"]').should('be.checked');
    cy.get('input[aria-label="Explicit zone distribution"]').check({ force: true });
    cy.get('input[aria-label="Zone us-central1-a"]').check({ force: true });
    cy.get('input[aria-label="Zone us-central1-b"]').check({ force: true });

    openWizardPage('Load Balancers');
    cy.get('select[aria-label="Load balancers"]').select(['pipeline-http-lb']);
    cy.get('select[aria-label="Backend services for pipeline-http-lb"]').select(['pipeline-backend']);
    cy.get('select[aria-label="Balancing mode"]').should('have.value', 'UTILIZATION');
    cy.get('input[aria-label="Capacity scaler"]').should('have.value', '100');

    openWizardPage('Policies');
    cy.get('[data-testid="enable-autoscaling"]').check({ force: true });
    cy.get('[data-testid="minimum-replicas"]').type('{selectall}1');
    cy.get('[data-testid="maximum-replicas"]').type('{selectall}5');
    cy.get('[data-testid="cooldown"]').type('{selectall}90');
    cy.get('[data-testid="cpu-target"]').type('{selectall}65');

    openWizardPage('Advanced Settings');
    cy.contains('.MapEditor', 'Custom Metadata').within(() => {
      cy.contains('button', 'Add New Metadata').click();
      cy.get('input[aria-label="Metadata key"]').last().type('round-trip-key');
      cy.get('input[aria-label="Metadata value"]').last().type('round-trip-value');
    });
    cy.get('input[aria-label="Disk size 1"]').type('{selectall}25');
    cy.get('[data-testid="add-network-tag"]').click();
    cy.get('input[aria-label="Network tag 1"]').type('round-trip-tag');
    submitWizard('Add');

    cy.contains('td', 'compute-pipeline-created').should('be.visible');
    cy.contains('tr', 'compute-pipeline-created').find('button[title="Edit"]').click();

    openWizardPage('Capacity/Distribution');
    cy.get('input[aria-label="Desired capacity"]').should('have.value', '3');
    cy.get('input[aria-label="Regional server group"]').should('be.checked');
    cy.get('input[aria-label="Explicit zone distribution"]').should('be.checked');
    cy.get('input[aria-label="Zone us-central1-a"]').should('be.checked');
    cy.get('input[aria-label="Zone us-central1-b"]').should('be.checked');
    openWizardPage('Load Balancers');
    cy.get('select[aria-label="Load balancers"] option:checked').should('have.value', 'pipeline-http-lb');
    cy.get('select[aria-label="Backend services for pipeline-http-lb"] option:checked').should(
      'have.value',
      'pipeline-backend',
    );
    openWizardPage('Policies');
    cy.get('[data-testid="enable-autoscaling"]').should('be.checked');
    cy.get('[data-testid="minimum-replicas"]').should('have.value', '1');
    cy.get('[data-testid="maximum-replicas"]').should('have.value', '5');
    cy.get('[data-testid="cooldown"]').should('have.value', '90');
    cy.get('[data-testid="cpu-target"]').should('have.value', '65');
    openWizardPage('Advanced Settings');
    cy.contains('.MapEditor', 'Custom Metadata').within(() => {
      cy.get('input[aria-label="Metadata key"]').should('have.value', 'round-trip-key');
      cy.get('input[aria-label="Metadata value"]').should('have.value', 'round-trip-value');
    });
    cy.get('input[aria-label="Disk size 1"]').should('have.value', '25');
    cy.get('input[aria-label="Network tag 1"]').should('have.value', 'round-trip-tag');
  });

  it('round-trips edits to an existing pipeline deployment', () => {
    cy.visit('#/applications/compute/executions/configure/030b5d92-f06c-474a-9d8d-d1d951529140');
    installGceTestHarness();
    openDeployStage();
    cy.get('.glyphicon-edit').first().click();

    cy.get('input[aria-label="Stack"]').type('{selectall}pipeline');
    cy.get('input[aria-label="Detail"]').type('{selectall}edited');
    submitWizard('Done');

    cy.contains('td', 'compute-pipeline-edited').should('be.visible');
    cy.get('.glyphicon-edit').first().click();
    cy.get('input[aria-label="Stack"]').should('have.value', 'pipeline');
    cy.get('input[aria-label="Detail"]').should('have.value', 'edited');
    submitWizard('Done');
  });
});

'use strict';

import { AccountService, ExpectedArtifactService } from '@spinnaker/core';

describe('Service: gceServerGroupCommandBuilder', function () {
  let builder;
  let $q;
  let $rootScope;
  let instanceTypeService;
  let gceCustomInstanceBuilderService;

  const gceXpnNamingService = {
    deriveProjectId: () => 'test-project',
    decorateXpnResourceIfNecessary: (_projectId, resource) => resource,
  };

  beforeEach(window.module(require('./serverGroupCommandBuilder.service').name));

  beforeEach(
    window.module(function ($provide) {
      instanceTypeService = {
        getCategories: angular.noop,
        getInstanceTypeDetails: angular.noop,
        getCategoryForInstanceType: angular.noop,
      };
      gceCustomInstanceBuilderService = {
        parseInstanceTypeString: angular.noop,
      };

      $provide.value('instanceTypeService', instanceTypeService);
      $provide.value('gceCustomInstanceBuilderService', gceCustomInstanceBuilderService);
      $provide.value('gceServerGroupHiddenMetadataKeys', []);
      $provide.value('gceXpnNamingService', gceXpnNamingService);
    }),
  );

  beforeEach(
    window.inject(function (gceServerGroupCommandBuilder, _$q_, _$rootScope_) {
      builder = gceServerGroupCommandBuilder;
      $q = _$q_;
      $rootScope = _$rootScope_;

      spyOn(AccountService, 'listAccounts').and.returnValue($q.when([{ name: 'test-account' }]));
      spyOn(ExpectedArtifactService, 'getExpectedArtifactsAvailableToStage').and.returnValue([]);
      spyOn(instanceTypeService, 'getCategories').and.returnValue(
        $q.when([{ type: 'custom', families: [{ instanceTypes: [{ name: 'n1-standard-1' }] }] }]),
      );
      spyOn(instanceTypeService, 'getInstanceTypeDetails').and.returnValue(
        $q.when({ storage: { localSSDSupported: false, size: 10, count: 1 } }),
      );
      spyOn(instanceTypeService, 'getCategoryForInstanceType').and.returnValue($q.when('custom'));
      spyOn(gceCustomInstanceBuilderService, 'parseInstanceTypeString').and.returnValue({});
    }),
  );

  function flush() {
    // chained $q and native thenables can require multiple digests to settle
    $rootScope.$digest();
    $rootScope.$digest();
    $rootScope.$digest();
  }

  function resolve(commandPromise) {
    let command;
    commandPromise.then((result) => {
      command = result;
    });
    flush();
    return command;
  }

  function buildServerGroup(shieldedConfigKey, shieldedConfigValue) {
    return {
      name: 'myapp-main-v000',
      moniker: { stack: 'main', detail: '' },
      account: 'test-account',
      asg: {
        minSize: 1,
        maxSize: 2,
        desiredCapacity: 1,
      },
      securityGroups: [],
      region: 'us-central1',
      regional: true,
      zones: ['us-central1-a'],
      canIpForward: false,
      distributionPolicy: null,
      selectZones: false,
      instanceTemplateLabels: {},
      launchConfig: {
        instanceType: 'n1-standard-1',
        minCpuPlatform: 'Intel',
        imageId: 'my-image',
        instanceTemplate: {
          properties: {
            networkInterfaces: [
              {
                network: 'projects/test/global/networks/default',
                subnetwork: 'projects/test/regions/us-central1/subnetworks/default',
              },
            ],
            scheduling: { preemptible: false, automaticRestart: true, onHostMaintenance: 'MIGRATE' },
            metadata: { items: [] },
            tags: { items: [] },
            serviceAccounts: [],
            resourceManagerTags: {},
            disks: [
              {
                initializeParams: {
                  diskType: 'pd-ssd',
                  diskSizeGb: 10,
                },
              },
            ],
            [shieldedConfigKey]: shieldedConfigValue,
          },
        },
      },
    };
  }

  it('does not initialize partnerMetadata on new commands', function () {
    const command = resolve(
      builder.buildNewServerGroupCommand(
        {
          name: 'myapp',
          attributes: {},
        },
        {},
      ),
    );

    expect(command).toBeDefined();
    expect(command.partnerMetadata).toBeUndefined();
  });

  it('hydrates clone shielded booleans from shieldedInstanceConfig', function () {
    const serverGroup = buildServerGroup('shieldedInstanceConfig', {
      enableSecureBoot: true,
      enableVtpm: true,
      enableIntegrityMonitoring: false,
    });
    const command = resolve(
      builder.buildServerGroupCommandFromExisting({ name: 'myapp', attributes: {} }, serverGroup),
    );

    expect(command.partnerMetadata).toBeUndefined();
    expect(command.enableSecureBoot).toBe(true);
    expect(command.enableVtpm).toBe(true);
    expect(command.enableIntegrityMonitoring).toBe(false);
  });

  it('falls back to legacy shieldedVmConfig when cloning', function () {
    const serverGroup = buildServerGroup('shieldedVmConfig', {
      enableSecureBoot: false,
      enableVtpm: true,
      enableIntegrityMonitoring: true,
    });
    const command = resolve(
      builder.buildServerGroupCommandFromExisting({ name: 'myapp', attributes: {} }, serverGroup),
    );

    expect(command.enableSecureBoot).toBe(false);
    expect(command.enableVtpm).toBe(true);
    expect(command.enableIntegrityMonitoring).toBe(true);
  });

  it('prefers shieldedInstanceConfig when both shielded keys exist', function () {
    const serverGroup = buildServerGroup('shieldedVmConfig', {
      enableSecureBoot: false,
      enableVtpm: true,
      enableIntegrityMonitoring: true,
    });
    serverGroup.launchConfig.instanceTemplate.properties.shieldedInstanceConfig = {
      enableSecureBoot: true,
      enableVtpm: false,
      enableIntegrityMonitoring: false,
    };
    const command = resolve(
      builder.buildServerGroupCommandFromExisting({ name: 'myapp', attributes: {} }, serverGroup),
    );

    expect(command.enableSecureBoot).toBe(true);
    expect(command.enableVtpm).toBe(false);
    expect(command.enableIntegrityMonitoring).toBe(false);
  });

  it('strips partnerMetadata from pipeline-derived commands', function () {
    const command = resolve(
      builder.buildServerGroupCommandFromPipeline(
        { name: 'myapp', attributes: {} },
        {
          account: 'test-account',
          zone: 'us-central1-a',
          availabilityZones: { 'us-central1': ['us-central1-a'] },
          instanceType: 'n1-standard-1',
          disks: [],
          tags: [],
          instanceMetadata: {},
          resourceManagerTags: {},
          partnerMetadata: {
            owner: { team: 'core' },
          },
        },
        {},
        {},
      ),
    );

    expect(command.partnerMetadata).toBeUndefined();
  });

  it('strips legacy maxUnavailable-only autoHealingPolicy from pipeline commands', function () {
    const command = resolve(
      builder.buildServerGroupCommandFromPipeline(
        { name: 'myapp', attributes: {} },
        {
          account: 'test-account',
          zone: 'us-central1-a',
          availabilityZones: { 'us-central1': ['us-central1-a'] },
          instanceType: 'n1-standard-1',
          disks: [],
          tags: [],
          instanceMetadata: {},
          resourceManagerTags: {},
          autoHealingPolicy: {
            maxUnavailable: { percent: 10 },
          },
        },
        {},
        {},
      ),
    );

    expect(command.autoHealingPolicy).toBeUndefined();
  });

  it('rebuilds pipeline autoHealingPolicy without maxUnavailable when health check is present', function () {
    const command = resolve(
      builder.buildServerGroupCommandFromPipeline(
        { name: 'myapp', attributes: {} },
        {
          account: 'test-account',
          zone: 'us-central1-a',
          availabilityZones: { 'us-central1': ['us-central1-a'] },
          instanceType: 'n1-standard-1',
          disks: [],
          tags: [],
          instanceMetadata: {},
          resourceManagerTags: {},
          autoHealingPolicy: {
            healthCheck: 'projects/test-project/global/healthChecks/example-hc',
            healthCheckUrl: 'projects/test-project/global/healthChecks/example-hc',
            initialDelaySec: 45,
            maxUnavailable: { fixed: 2 },
          },
        },
        {},
        {},
      ),
    );

    expect(command.autoHealingPolicy.healthCheck).toBe('example-hc');
    expect(command.autoHealingPolicy.initialDelaySec).toBe(45);
    expect(command.autoHealingPolicy.maxUnavailable).toBeUndefined();
  });

  it('deep-clones instanceFlexibilityPolicy when cloning an existing server group', function () {
    const serverGroup = buildServerGroup('shieldedInstanceConfig', {
      enableSecureBoot: false,
      enableVtpm: false,
      enableIntegrityMonitoring: false,
    });
    serverGroup.instanceFlexibilityPolicy = {
      instanceSelections: {
        preferred: { machineTypes: ['n2-standard-8'] },
        fallback: { rank: 1, machineTypes: ['e2-standard-8'] },
      },
    };
    const command = resolve(
      builder.buildServerGroupCommandFromExisting({ name: 'myapp', attributes: {} }, serverGroup),
    );

    expect(command.instanceFlexibilityPolicy).toEqual(serverGroup.instanceFlexibilityPolicy);
    expect(command.instanceFlexibilityPolicy).not.toBe(serverGroup.instanceFlexibilityPolicy);
    command.instanceFlexibilityPolicy.instanceSelections.preferred.machineTypes.push('c3-standard-8');
    expect(serverGroup.instanceFlexibilityPolicy.instanceSelections.preferred.machineTypes).toEqual(['n2-standard-8']);
  });

  it('does not hydrate autoHealingPolicy.maxUnavailable onto clone commands', function () {
    const serverGroup = buildServerGroup('shieldedInstanceConfig', {
      enableSecureBoot: false,
      enableVtpm: false,
      enableIntegrityMonitoring: false,
    });
    serverGroup.autoHealingPolicy = {
      healthCheck: 'projects/test/global/healthChecks/hc',
      healthCheckUrl: 'projects/test/global/healthChecks/hc',
      initialDelaySec: 60,
      maxUnavailable: { fixed: 3 },
    };
    const command = resolve(
      builder.buildServerGroupCommandFromExisting({ name: 'myapp', attributes: {} }, serverGroup),
    );

    expect(command.autoHealingPolicy.healthCheck).toBe('hc');
    expect(command.autoHealingPolicy.initialDelaySec).toBe(60);
    expect(command.autoHealingPolicy.maxUnavailable).toBeUndefined();
  });
});

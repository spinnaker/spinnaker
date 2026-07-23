'use strict';

import { AccountService, ExpectedArtifactService } from '@spinnaker/core';

import { GceServerGroupCommandBuilder } from './serverGroupCommandBuilder.service';

describe('GceServerGroupCommandBuilder', () => {
  let builder;
  let instanceTypeService;
  let customInstanceBuilder;
  let xpnNamingService;

  beforeEach(() => {
    spyOn(AccountService, 'listAccounts').and.resolveTo([{ name: 'test-account' }]);
    spyOn(ExpectedArtifactService, 'getExpectedArtifactsAvailableToStage').and.returnValue([]);
    instanceTypeService = jasmine.createSpyObj('instanceTypeService', [
      'getCategories',
      'getInstanceTypeDetails',
      'getCategoryForInstanceType',
    ]);
    instanceTypeService.getCategories.and.resolveTo([
      { type: 'custom', families: [{ instanceTypes: [{ name: 'n1-standard-1' }] }] },
    ]);
    instanceTypeService.getInstanceTypeDetails.and.resolveTo({
      storage: { localSSDSupported: false, size: 10, count: 1 },
    });
    instanceTypeService.getCategoryForInstanceType.and.resolveTo('custom');
    customInstanceBuilder = jasmine.createSpyObj('customInstanceBuilder', ['parseInstanceTypeString']);
    customInstanceBuilder.parseInstanceTypeString.and.returnValue({});
    xpnNamingService = jasmine.createSpyObj('xpnNamingService', ['deriveProjectId', 'decorateXpnResourceIfNecessary']);
    xpnNamingService.deriveProjectId.and.returnValue('test-project');
    xpnNamingService.decorateXpnResourceIfNecessary.and.callFake((_projectId, resource) => resource);

    const $q = { all: (values) => Promise.all(values), when: (value) => Promise.resolve(value) };
    builder = new GceServerGroupCommandBuilder($q, instanceTypeService, customInstanceBuilder, xpnNamingService, []);
  });

  function application() {
    return { name: 'test-app', attributes: {} };
  }

  function buildServerGroup() {
    return {
      name: 'test-app-main-v000',
      moniker: { stack: 'main', detail: '' },
      account: 'test-account',
      asg: {
        minSize: 1,
        maxSize: 2,
        desiredCapacity: 1,
        'backend-service-names': [],
      },
      securityGroups: [],
      region: 'us-central1',
      regional: true,
      zones: ['us-central1-a'],
      canIpForward: false,
      distributionPolicy: { zones: ['us-central1-a'], targetShape: 'ANY_SINGLE_ZONE' },
      selectZones: false,
      instanceTemplateLabels: {},
      launchConfig: {
        instanceType: 'n1-standard-1',
        minCpuPlatform: 'Intel',
        imageId: 'test-image',
        instanceTemplate: {
          selfLink: 'https://compute/projects/test-project/global/instanceTemplates/test-template',
          properties: {
            networkInterfaces: [
              {
                network: 'projects/test-project/global/networks/default',
                subnetwork: 'projects/test-project/regions/us-central1/subnetworks/default',
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
          },
        },
      },
    };
  }

  function pipelineCluster(overrides = {}) {
    return {
      account: 'test-account',
      zone: 'us-central1-a',
      availabilityZones: { 'us-central1': ['us-central1-a'] },
      instanceType: 'n1-standard-1',
      disks: [],
      tags: [],
      instanceMetadata: {},
      resourceManagerTags: {},
      ...overrides,
    };
  }

  it('builds new commands without unsupported partner metadata', async () => {
    const command = await builder.buildNewServerGroupCommand(application(), {});

    expect(command.partnerMetadata).toBeUndefined();
  });

  it('builds clone commands with exact supported policy and distribution fields', async () => {
    const serverGroup = buildServerGroup();
    serverGroup.partnerMetadata = { legacy: true };
    serverGroup.autoHealingPolicy = {
      healthCheck: 'projects/test-project/global/healthChecks/example-health-check',
      healthCheckUrl: 'projects/test-project/global/healthChecks/example-health-check',
      healthCheckKind: 'healthCheck',
      initialDelaySec: 45,
      maxUnavailable: { fixed: 2 },
      unknownPolicyField: 'discard',
    };
    serverGroup.instanceFlexibilityPolicy = {
      instanceSelections: {
        preferred: { machineTypes: ['n2-standard-8'] },
      },
    };

    const command = await builder.buildServerGroupCommandFromExisting(application(), serverGroup);

    expect(command.partnerMetadata).toBeUndefined();
    expect(command.autoHealingPolicy).toEqual({
      healthCheck: 'example-health-check',
      healthCheckKind: 'healthCheck',
      healthCheckUrl: 'projects/test-project/global/healthChecks/example-health-check',
      initialDelaySec: 45,
    });
    expect(command.distributionPolicy).toEqual({
      zones: ['us-central1-a'],
      targetShape: 'ANY_SINGLE_ZONE',
    });
    expect(command.selectZones).toBe(false);
    expect(command.instanceFlexibilityPolicy).toEqual(serverGroup.instanceFlexibilityPolicy);
    expect(command.instanceFlexibilityPolicy).not.toBe(serverGroup.instanceFlexibilityPolicy);
    command.instanceFlexibilityPolicy.instanceSelections.preferred.machineTypes.push('c3-standard-8');
    expect(serverGroup.instanceFlexibilityPolicy.instanceSelections.preferred.machineTypes).toEqual(['n2-standard-8']);
  });

  it('preserves absent versus explicitly empty clone flexibility policies', async () => {
    const absent = buildServerGroup();
    const empty = buildServerGroup();
    empty.instanceFlexibilityPolicy = { instanceSelections: {} };

    const absentCommand = await builder.buildServerGroupCommandFromExisting(application(), absent);
    const emptyCommand = await builder.buildServerGroupCommandFromExisting(application(), empty);

    expect(absentCommand.instanceFlexibilityPolicy).toBeUndefined();
    expect(emptyCommand.instanceFlexibilityPolicy).toEqual({ instanceSelections: {} });
  });

  it('hydrates shielded fields with stable, legacy, then top-level precedence while preserving false', async () => {
    const serverGroup = buildServerGroup();
    serverGroup.launchConfig.instanceTemplate.properties.shieldedInstanceConfig = {
      enableSecureBoot: false,
    };
    serverGroup.launchConfig.instanceTemplate.properties.shieldedVmConfig = {
      enableSecureBoot: true,
      enableVtpm: false,
    };
    serverGroup.enableSecureBoot = true;
    serverGroup.enableVtpm = true;
    serverGroup.enableIntegrityMonitoring = false;

    const command = await builder.buildServerGroupCommandFromExisting(application(), serverGroup);

    expect(command.enableSecureBoot).toBe(false);
    expect(command.enableVtpm).toBe(false);
    expect(command.enableIntegrityMonitoring).toBe(false);
  });

  it('strips unsupported fields and deep-clones flexibility from pipeline commands', async () => {
    const flexibilityPolicy = {
      instanceSelections: {
        preferred: { machineTypes: ['n2-standard-8'] },
      },
    };
    const originalCluster = pipelineCluster({
      partnerMetadata: { legacy: true },
      distributionPolicy: { zones: ['us-central1-a'], targetShape: 'BALANCED' },
      selectZones: false,
      instanceFlexibilityPolicy: flexibilityPolicy,
      autoHealingPolicy: {
        healthCheck: 'projects/test-project/global/httpHealthChecks/example-health-check',
        healthCheckUrl: 'projects/test-project/global/httpHealthChecks/example-health-check',
        healthCheckKind: 'httpHealthCheck',
        initialDelaySec: 60,
        maxUnavailable: { percent: 10 },
      },
    });

    const command = await builder.buildServerGroupCommandFromPipeline(application(), originalCluster, {}, {});

    expect(command.partnerMetadata).toBeUndefined();
    expect(command.autoHealingPolicy).toEqual({
      healthCheck: 'example-health-check',
      healthCheckKind: 'httpHealthCheck',
      healthCheckUrl: 'projects/test-project/global/httpHealthChecks/example-health-check',
      initialDelaySec: 60,
    });
    expect(command.distributionPolicy).toEqual({
      zones: ['us-central1-a'],
      targetShape: 'BALANCED',
    });
    expect(command.selectZones).toBe(false);
    expect(command.instanceFlexibilityPolicy).toEqual(flexibilityPolicy);
    expect(command.instanceFlexibilityPolicy).not.toBe(flexibilityPolicy);
    command.instanceFlexibilityPolicy.instanceSelections.preferred.machineTypes.push('c3-standard-8');
    expect(flexibilityPolicy.instanceSelections.preferred.machineTypes).toEqual(['n2-standard-8']);
  });

  it('removes a pipeline auto-healing policy that contains only unsupported legacy fields', async () => {
    const command = await builder.buildServerGroupCommandFromPipeline(
      application(),
      pipelineCluster({ autoHealingPolicy: { maxUnavailable: { percent: 10 } } }),
      {},
      {},
    );

    expect(command.autoHealingPolicy).toBeUndefined();
  });
});

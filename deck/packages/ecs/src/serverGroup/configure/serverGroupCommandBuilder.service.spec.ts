import { AccountService } from '@spinnaker/core';

import { EcsServerGroupCommandBuilder } from './serverGroupCommandBuilder.service';

describe('EcsServerGroupCommandBuilder', () => {
  it('builds a new server group command when no defaults are provided (create flow passes null)', async () => {
    vi.spyOn(AccountService, 'getAvailabilityZonesForAccountAndRegion').mockReturnValue(
      Promise.resolve(['us-west-2a']),
    );
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockReturnValue(Promise.resolve({}));

    const command = await new EcsServerGroupCommandBuilder().buildNewServerGroupCommand(
      {
        defaultCredentials: { ecs: 'ecs-prod' },
        defaultRegions: { ecs: 'us-west-2' },
        name: 'api',
      } as any,
      null,
    );

    expect(command.credentials).toBe('ecs-prod');
    expect(command.region).toBe('us-west-2');
    expect(command.copySourceScalingPoliciesAndActions).toBe(true);
    expect(command.copySourceMonitoringConfiguration).toBe(true);
    expect(command.monitoringConfiguration).toBeUndefined();
  });

  it('builds pipeline commands when availability zones are missing', async () => {
    vi.spyOn(AccountService, 'getAvailabilityZonesForAccountAndRegion').mockReturnValue(
      Promise.resolve(['us-west-2a']),
    );
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockReturnValue(Promise.resolve({}));

    const command = await new EcsServerGroupCommandBuilder().buildServerGroupCommandFromPipeline(
      {
        defaultCredentials: { ecs: 'ecs-prod' },
        defaultRegions: { ecs: 'us-west-2' },
        name: 'api',
      } as any,
      {
        account: 'ecs-prod',
        capacity: { min: 1, max: 1 },
        useSourceCapacity: false,
      },
      { requisiteStageRefIds: [], refId: '1', type: 'deploy' },
      { stages: [], triggers: [] },
    );

    expect(command.region).toBe('us-west-2');
    expect(command.availabilityZones).toEqual(['us-west-2a']);
  });

  it('builds pipeline commands when source capacity omits explicit capacity', async () => {
    vi.spyOn(AccountService, 'getAvailabilityZonesForAccountAndRegion').mockReturnValue(
      Promise.resolve(['us-west-2a']),
    );
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockReturnValue(Promise.resolve({}));

    const command = await new EcsServerGroupCommandBuilder().buildServerGroupCommandFromPipeline(
      {
        defaultCredentials: { ecs: 'ecs-prod' },
        defaultRegions: { ecs: 'us-west-2' },
        name: 'api',
      } as any,
      {
        account: 'ecs-prod',
        useSourceCapacity: true,
      },
      { requisiteStageRefIds: [], refId: '1', type: 'deploy' },
      { stages: [], triggers: [] },
    );

    expect(command.viewState.useSimpleCapacity).toBe(false);
  });

  describe('buildServerGroupCommandFromExisting', () => {
    const application = {
      defaultCredentials: { ecs: 'ecs-prod' },
      defaultRegions: { ecs: 'us-west-2' },
      name: 'api',
    } as any;
    const existing = (overrides: any) => ({
      account: 'ecs-prod',
      capacity: { min: 1, max: 1, desired: 1 },
      cloudProvider: 'ecs',
      ecsCluster: 'main',
      moniker: { app: 'api', stack: 'main' },
      region: 'us-west-2',
      type: 'ecs',
      ...overrides,
    });

    beforeEach(() => {
      vi.spyOn(AccountService, 'getAvailabilityZonesForAccountAndRegion').mockResolvedValue(['us-west-2a']);
      vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockResolvedValue({});
    });

    it('selects ecs-native for a server group carrying the isNative flag', async () => {
      const command = await new EcsServerGroupCommandBuilder().buildServerGroupCommandFromExisting(
        application,
        existing({ isNative: true }),
        'editPipeline',
      );
      expect(command.cloudProvider).toBe('ecs-native');
    });

    it('leaves classic ECS server groups on the ecs provider', async () => {
      const command = await new EcsServerGroupCommandBuilder().buildServerGroupCommandFromExisting(
        application,
        existing({}),
        'editPipeline',
      );
      expect(command.cloudProvider).toBeUndefined();
    });
  });

  it('preserves cloudProvider ecs-native on pipeline clusters', async () => {
    vi.spyOn(AccountService, 'getAvailabilityZonesForAccountAndRegion').mockResolvedValue(['us-west-2a']);
    vi.spyOn(AccountService, 'getCredentialsKeyedByAccount').mockResolvedValue({});

    const command = await new EcsServerGroupCommandBuilder().buildServerGroupCommandFromPipeline(
      { defaultCredentials: { ecs: 'ecs-prod' }, defaultRegions: { ecs: 'us-west-2' }, name: 'api' } as any,
      { account: 'ecs-prod', cloudProvider: 'ecs-native', useSourceCapacity: true },
      { requisiteStageRefIds: [], refId: '1', type: 'deploy' },
      { stages: [], triggers: [] },
    );

    expect(command.cloudProvider).toBe('ecs-native');
  });

  it('builds update commands for ECS server groups without ASG metadata', () => {
    const command = new EcsServerGroupCommandBuilder().buildUpdateServerGroupCommand({
      account: 'ecs-prod',
      name: 'api-main-v001',
      region: 'eu-west-1',
    } as any);

    expect(command).toEqual(
      expect.objectContaining({
        asgs: [{ asgName: 'api-main-v001', region: 'eu-west-1' }],
        credentials: 'ecs-prod',
        healthCheckType: undefined,
        type: 'modifyAsg',
      }),
    );
  });
});

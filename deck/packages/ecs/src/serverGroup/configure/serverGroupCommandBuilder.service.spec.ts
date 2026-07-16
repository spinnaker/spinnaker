import { AccountService } from '@spinnaker/core';

import { EcsServerGroupCommandBuilder } from './serverGroupCommandBuilder.service';

describe('EcsServerGroupCommandBuilder', () => {
  it('builds pipeline commands when availability zones are missing', async () => {
    spyOn(AccountService, 'getAvailabilityZonesForAccountAndRegion').and.returnValue(Promise.resolve(['us-west-2a']));
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(Promise.resolve({}));

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
    spyOn(AccountService, 'getAvailabilityZonesForAccountAndRegion').and.returnValue(Promise.resolve(['us-west-2a']));
    spyOn(AccountService, 'getCredentialsKeyedByAccount').and.returnValue(Promise.resolve({}));

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

  it('builds update commands for ECS server groups without ASG metadata', () => {
    const command = new EcsServerGroupCommandBuilder().buildUpdateServerGroupCommand({
      account: 'ecs-prod',
      name: 'api-main-v001',
      region: 'eu-west-1',
    } as any);

    expect(command).toEqual(
      jasmine.objectContaining({
        asgs: [{ asgName: 'api-main-v001', region: 'eu-west-1' }],
        credentials: 'ecs-prod',
        healthCheckType: undefined,
        type: 'modifyAsg',
      }),
    );
  });
});

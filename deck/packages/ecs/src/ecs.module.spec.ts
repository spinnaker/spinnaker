import { CloudProviderRegistry, Registry } from '@spinnaker/core';

import * as ecs from './index';
import { registerEcsPipelineStages } from './ecs.module';
import { EcsSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { EcsSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { EcsServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { EcsServerGroupTransformer } from './serverGroup/serverGroup.transformer';

describe('ECS package registration', () => {
  it('registers ECS without exporting an Angular module token', () => {
    expect((ecs as any)['ECS' + '_MODULE']).toBeUndefined();
    expect(CloudProviderRegistry.getValue('ecs', 'serverGroup.transformer')).toBe(EcsServerGroupTransformer);
    expect(CloudProviderRegistry.getValue('ecs', 'serverGroup.commandBuilder')).toBe(EcsServerGroupCommandBuilder);
    expect(CloudProviderRegistry.getValue('ecs', 'securityGroup.reader')).toBe(EcsSecurityGroupReader);
    expect(CloudProviderRegistry.getValue('ecs', 'securityGroup.transformer')).toBe(EcsSecurityGroupTransformer);
    expect(
      new (CloudProviderRegistry.getValue('ecs', 'serverGroup.commandBuilder') as any)().buildNewServerGroupCommand,
    ).toEqual(jasmine.any(Function));
    expect(
      new (CloudProviderRegistry.getValue('ecs', 'securityGroup.transformer') as any)().normalizeSecurityGroup,
    ).toEqual(jasmine.any(Function));
    const controllerKey = 'Cont' + 'roller';
    const templateUrlKey = 'Template' + 'Url';
    expect(CloudProviderRegistry.getValue('ecs', `serverGroup.details${controllerKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('ecs', `serverGroup.details${templateUrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('ecs', `instance.details${controllerKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('ecs', `securityGroup.details${controllerKey}`)).toBeNull();
  });

  it('registers ECS pipeline stages without an Angular module dependency', () => {
    registerEcsPipelineStages();
    const stages = Registry.pipeline.getStageTypes();
    expect(stages.find((stage) => stage.cloudProvider === 'ecs' && stage.provides === 'destroyServerGroup')).toEqual(
      jasmine.objectContaining({ cloudProvider: 'ecs', provides: 'destroyServerGroup' }),
    );
    expect(stages.find((stage) => stage.cloudProvider === 'ecs' && stage.provides === 'resizeServerGroup')).toEqual(
      jasmine.objectContaining({ cloudProvider: 'ecs', provides: 'resizeServerGroup' }),
    );
  });

  it('normalizes ECS security groups as resolved values', async () => {
    const securityGroup = { name: 'sg-web' };

    const normalized = await new EcsSecurityGroupTransformer().normalizeSecurityGroup(securityGroup);

    expect(normalized).toBe(securityGroup);
  });
});

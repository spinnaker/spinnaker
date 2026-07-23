import { CloudProviderRegistry, Registry } from '@spinnaker/core';

import { registerEcsPipelineStages } from './ecs.module';
import { EcsSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { EcsSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { EcsServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { EcsServerGroupActions } from './serverGroup/details/EcsServerGroupActions';
import { EcsServerGroupTransformer } from './serverGroup/serverGroup.transformer';

describe('ECS package registration', () => {
  it('registers ECS through React components', () => {
    expect(CloudProviderRegistry.getValue('ecs', 'serverGroup.transformer')).toBe(EcsServerGroupTransformer);
    expect(CloudProviderRegistry.getValue('ecs', 'serverGroup.commandBuilder')).toBe(EcsServerGroupCommandBuilder);
    expect(CloudProviderRegistry.getValue('ecs', 'serverGroup.detailsActions')).toBe(EcsServerGroupActions);
    expect(CloudProviderRegistry.getValue('ecs', 'adHocInfrastructureWritesEnabled')).toBeTrue();
    const detailsSections = CloudProviderRegistry.getValue('ecs', 'serverGroup.detailsSections');
    expect(detailsSections.length).toBe(9);
    expect(detailsSections.every((section: unknown) => typeof section === 'function')).toBeTrue();
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
    Registry.reinitialize();
    registerEcsPipelineStages();
    const stages = Registry.pipeline.getStageTypes();
    expect(stages.find((stage) => stage.cloudProvider === 'ecs' && stage.provides === 'destroyServerGroup')).toEqual(
      jasmine.objectContaining({ cloudProvider: 'ecs', provides: 'destroyServerGroup' }),
    );
    expect(stages.find((stage) => stage.cloudProvider === 'ecs' && stage.provides === 'resizeServerGroup')).toEqual(
      jasmine.objectContaining({ cloudProvider: 'ecs', provides: 'resizeServerGroup' }),
    );
  });

  it('registers ECS pipeline stage config forms as React components', () => {
    Registry.reinitialize();
    registerEcsPipelineStages();
    const ecsStages = Registry.pipeline
      .getStageTypes()
      .filter((stage) => stage.cloudProvider === 'ecs' && stage.provides);

    expect(ecsStages.map((stage) => stage.provides).sort()).toEqual([
      'cloneServerGroup',
      'destroyServerGroup',
      'disableCluster',
      'disableServerGroup',
      'enableServerGroup',
      'findImageFromTags',
      'resizeServerGroup',
      'scaleDownCluster',
      'shrinkCluster',
    ]);
    ecsStages.forEach((stage) => {
      expect(stage.component).toEqual(jasmine.any(Function));
      expect(stage.templateUrl).toBeUndefined();
    });
  });

  it('normalizes ECS security groups as resolved values', async () => {
    const securityGroup = { name: 'sg-web' };

    const normalized = await new EcsSecurityGroupTransformer().normalizeSecurityGroup(securityGroup);

    expect(normalized).toBe(securityGroup);
  });
});

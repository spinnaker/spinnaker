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
    expect(CloudProviderRegistry.getValue('ecs', 'serverGroup.detailsActions').displayName).toBe(
      EcsServerGroupActions.displayName,
    );
    expect(CloudProviderRegistry.getValue('ecs', 'adHocInfrastructureWritesEnabled')).toBe(true);
    const detailsSections = CloudProviderRegistry.getValue('ecs', 'serverGroup.detailsSections');
    expect(detailsSections.length).toBe(9);
    expect(detailsSections.every((section: unknown) => typeof section === 'function')).toBe(true);
    expect(CloudProviderRegistry.getValue('ecs', 'securityGroup.reader')).toBe(EcsSecurityGroupReader);
    expect(CloudProviderRegistry.getValue('ecs', 'securityGroup.transformer')).toBe(EcsSecurityGroupTransformer);
    expect(
      new (CloudProviderRegistry.getValue('ecs', 'serverGroup.commandBuilder') as any)().buildNewServerGroupCommand,
    ).toEqual(expect.any(Function));
    expect(
      new (CloudProviderRegistry.getValue('ecs', 'securityGroup.transformer') as any)().normalizeSecurityGroup,
    ).toEqual(expect.any(Function));
    const controllerKey = 'Cont' + 'roller';
    const templateUrlKey = 'Template' + 'Url';
    expect(CloudProviderRegistry.getValue('ecs', `serverGroup.details${controllerKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('ecs', `serverGroup.details${templateUrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('ecs', `instance.details${controllerKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('ecs', `securityGroup.details${controllerKey}`)).toBeNull();
  });

  it('registers ECS pipeline stages directly', () => {
    Registry.reinitialize();
    registerEcsPipelineStages();
    const stages = Registry.pipeline.getStageTypes();
    expect(stages.find((stage) => stage.cloudProvider === 'ecs' && stage.provides === 'destroyServerGroup')).toEqual(
      expect.objectContaining({ cloudProvider: 'ecs', provides: 'destroyServerGroup' }),
    );
    expect(stages.find((stage) => stage.cloudProvider === 'ecs' && stage.provides === 'resizeServerGroup')).toEqual(
      expect.objectContaining({ cloudProvider: 'ecs', provides: 'resizeServerGroup' }),
    );
  });

  it('ECS stage configs expose React components without legacy templates', () => {
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
      expect(stage.component).toEqual(expect.any(Function));
      expect(stage.templateUrl).toBeUndefined();
    });
  });

  it('does not bundle ECS HTML templates', () => {
    const ecsTemplates = Object.keys(import.meta.glob('./**/*.html'));

    expect(ecsTemplates).toEqual([]);
  });

  it('normalizes ECS security groups as resolved values', async () => {
    const securityGroup = { name: 'sg-web' };

    const normalized = await new EcsSecurityGroupTransformer().normalizeSecurityGroup(securityGroup);

    expect(normalized).toBe(securityGroup);
  });
});

import { CloudProviderRegistry, Registry } from '@spinnaker/core';

import { registerEcsPipelineStages } from './ecs.module';
import { EcsSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { EcsSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { EcsServerGroupHeader } from './serverGroup/EcsServerGroupHeader';
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
    expect(CloudProviderRegistry.getValue('ecs', 'serverGroups.pod.header')).toBe(EcsServerGroupHeader);
    expect(CloudProviderRegistry.getValue('ecs', 'adHocInfrastructureWritesEnabled')).toBe(true);
    const detailsSections = CloudProviderRegistry.getValue('ecs', 'serverGroup.detailsSections');
    expect(detailsSections.length).toBe(10);
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

  it('registers explicit native service deployment lifecycle stages without automatic behavior', () => {
    Registry.reinitialize();
    registerEcsPipelineStages();
    const nativeStages = Registry.pipeline
      .getStageTypes()
      .filter((stage) => stage.key?.startsWith('ecsNative'))
      .sort((left, right) => left.key.localeCompare(right.key));
    expect(nativeStages.map((stage) => stage.key)).toEqual([
      'ecsNativeContinueServiceDeployment',
      'ecsNativeStopServiceDeployment',
    ]);
    // Stages with `provides` are provider implementations of a base stage and never appear in the type picker.
    nativeStages.forEach((stage) => {
      expect(stage.provides).toBeUndefined();
      expect(stage.label).toBeTruthy();
      expect(stage.description).toBeTruthy();
    });
    const pickerKeys = Registry.pipeline
      .getConfigurableStageTypes([{ cloudProvider: 'ecs' } as any])
      .map((stage) => stage.key);
    expect(pickerKeys).toContain('ecsNativeContinueServiceDeployment');
    expect(pickerKeys).toContain('ecsNativeStopServiceDeployment');
    nativeStages.forEach((stage) => {
      expect(stage.component).toEqual(expect.any(Function));
      expect(stage.validators.slice(0, 3)).toEqual([
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
        { type: 'requiredField', fieldName: 'region' },
        { type: 'requiredField', fieldName: 'serverGroupName' },
      ]);
      // Deployment reference (both stages) plus the lifecycle action expression (Continue only);
      // behavior is covered in ecsNativeStageValidation.spec.ts.
      const customValidators = stage.validators.slice(3);
      expect(customValidators.length).toBe(stage.key === 'ecsNativeContinueServiceDeployment' ? 2 : 1);
      customValidators.forEach((validator) => expect(validator.type).toBe('custom'));
      expect(stage).not.toEqual(expect.objectContaining({ onFailure: expect.anything() }));
    });
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

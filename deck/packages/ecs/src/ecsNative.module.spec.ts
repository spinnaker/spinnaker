import { CloudProviderRegistry, DeploymentStrategyRegistry, SETTINGS } from '@spinnaker/core';

import { registerEcsNativeProvider } from './ecsNative.module';
import { EcsServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { EcsCloneServerGroupModal } from './serverGroup/configure/wizard/EcsCloneServerGroupModal';
import { EcsServerGroupHeader } from './serverGroup/EcsServerGroupHeader';
import { EcsServerGroupTransformer } from './serverGroup/serverGroup.transformer';

describe('registerEcsNativeProvider', () => {
  it('registers ecs-native even without its own SETTINGS.providers entry, and leaves none behind', () => {
    delete SETTINGS.providers['ecs-native'];

    registerEcsNativeProvider();

    expect(CloudProviderRegistry.getProvider('ecs-native')).toBeTruthy();
    expect(SETTINGS.providers['ecs-native']).toBeUndefined();
    expect(Object.keys(SETTINGS.providers)).not.toContain('ecs-native');
  });

  it('is not offered as an account type, because native deploys use ecs accounts', () => {
    registerEcsNativeProvider();

    const accountTypes = CloudProviderRegistry.listRegisteredAccountTypes();
    expect(accountTypes).toContain('ecs');
    expect(accountTypes).not.toContain('ecs-native');
  });

  it('still resolves the components that core looks up by a stage cluster cloudProvider of ecs-native', () => {
    registerEcsNativeProvider();

    // DeployStageConfig.editCluster: CloneServerGroupModal + serverGroup.commandBuilder delegate.
    expect(CloudProviderRegistry.getValue('ecs-native', 'serverGroup.CloneServerGroupModal')).toEqual(
      EcsCloneServerGroupModal,
    );
    expect(CloudProviderRegistry.getValue('ecs-native', 'serverGroup.commandBuilder')).toBe(
      EcsServerGroupCommandBuilder,
    );
    // CloudProviderLogo / CloudProviderLabel in the deploy-stage cluster table.
    expect(CloudProviderRegistry.getValue('ecs-native', 'name')).toBe('EC2 Container Service (Native)');
    expect(CloudProviderRegistry.getValue('ecs-native', 'logo.path')).toBeTruthy();
  });

  it('registers ecs-native as a clone of the ecs provider config, with its own name', () => {
    registerEcsNativeProvider();

    const ecsConfig = CloudProviderRegistry.getProvider('ecs');
    const ecsNativeConfig = CloudProviderRegistry.getProvider('ecs-native');

    expect(ecsNativeConfig).toBeTruthy();
    expect(ecsNativeConfig.name).not.toBe(ecsConfig.name);
    expect(ecsNativeConfig.serverGroups.pod.header).toBe(EcsServerGroupHeader);
  });

  it('reuses every other ecs component reference as-is', () => {
    registerEcsNativeProvider();

    const ecsNativeConfig = CloudProviderRegistry.getProvider('ecs-native');

    expect(ecsNativeConfig.serverGroup.transformer).toBe(EcsServerGroupTransformer);
    expect(ecsNativeConfig.serverGroup.CloneServerGroupModal).toEqual(EcsCloneServerGroupModal);
  });

  it('does not add ecs-native to any provider-restricted deployment strategy, so redblack (offered for ecs) is excluded', () => {
    const ecsStrategies = DeploymentStrategyRegistry.listStrategies('ecs').map((s) => s.key);
    const ecsNativeStrategies = DeploymentStrategyRegistry.listStrategies('ecs-native').map((s) => s.key);

    expect(ecsStrategies).toContain('redblack');
    expect(ecsNativeStrategies).not.toContain('redblack');
  });
});

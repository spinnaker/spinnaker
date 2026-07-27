import type { DeckRuntime } from './DeckRuntime';
import '../application/application.module';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import '../ci/ci.module';
import '../cluster/cluster.module';
import '../deploymentStrategy/deploymentStrategy.module';
import type { IStageTypeConfig } from '../domain';
import { registerEntityTagsDataSource } from '../entityTag/entityTags.dataSource';
import '../entityTag/entityTags.module';
import { createDirectFunctionReader, registerFunctionDataSource } from '../function/function.dataSource';
import '../instance/instance.module';
import { registerLoadBalancerDataSource } from '../loadBalancer/loadBalancer.dataSource';
import '../loadBalancer/loadBalancer.module';
import { registerBuiltinNotificationTypes } from '../notification/notification.types';
import { initializeDynamicNotificationTypes } from '../notification/notifications.module';
import { registerDeployStage } from '../pipeline/config/stages/deploy/deployStage';
import { registerPreconfiguredJobStages } from '../pipeline/config/stages/preconfiguredJob/preconfiguredJobStage';
import { registerScriptStage } from '../pipeline/config/stages/script/scriptStage';
import { registerPreconfiguredWebhookStages } from '../pipeline/config/stages/webhook/webhookStage';
import { registerPipelineDataSources } from '../pipeline/pipeline.dataSource';
import '../pipeline/pipeline.module';
import { Registry } from '../registry';
import { registerSearchFilterTypes } from '../search/widgets/searchFilterTypeRegistrations';
import { registerSecurityGroupDataSource } from '../securityGroup/securityGroup.dataSource';
import '../securityGroup/securityGroup.module';
import { registerServerGroupDataSource } from '../serverGroup/serverGroup.dataSource';
import '../serverGroup/serverGroup.module';
import { registerTaskDataSources } from '../task/task.dataSource';

import '../cloudProvider/cloudProviderLogo.less';
import '../cluster/rollups.less';
import '../insight/insight.less';
import '../modal/modals.less';
import '../presentation/navigation/pageNavigation.less';
import '../task/tasks.less';
import '../widgets/spelText/spel.less';

interface RuntimeMetadataRegistration {
  runtime: DeckRuntime;
  dataSourceKeys: string[];
  deployStage: IStageTypeConfig;
}

let activeRuntimeMetadata: RuntimeMetadataRegistration | null = null;
let dynamicRuntimeMetadataAttempt: Promise<void> | null = null;

export function registerRuntimeDataSources(runtime: DeckRuntime): string[] {
  const existingKeys = new Set(ApplicationDataSourceRegistry.getDataSources().map(({ key }) => key));
  const { promiseService, services } = runtime;
  registerServerGroupDataSource(services.clusterService);
  registerLoadBalancerDataSource(promiseService, services.loadBalancerReader);
  registerSecurityGroupDataSource(services.securityGroupReader);
  registerFunctionDataSource(createDirectFunctionReader(services.providerServiceDelegate));
  registerEntityTagsDataSource();
  registerPipelineDataSources(promiseService, services.executionService, services.clusterService);
  registerTaskDataSources(promiseService, services.clusterService);
  return ApplicationDataSourceRegistry.getDataSources()
    .map(({ key }) => key)
    .filter((key) => !existingKeys.has(key));
}

export function initializeRuntimeMetadata(runtime: DeckRuntime): void {
  if (activeRuntimeMetadata?.runtime === runtime) {
    return;
  }
  disposeRuntimeMetadata();

  const dataSourceKeys = registerRuntimeDataSources(runtime);
  const deployStage = registerDeployStage(runtime.services.clusterService);
  registerScriptStage();
  registerBuiltinNotificationTypes();
  registerSearchFilterTypes();
  activeRuntimeMetadata = { runtime, dataSourceKeys, deployStage };
}

export function disposeRuntimeMetadata(runtime?: DeckRuntime): void {
  if (!activeRuntimeMetadata || (runtime && activeRuntimeMetadata.runtime !== runtime)) {
    return;
  }

  activeRuntimeMetadata.dataSourceKeys.forEach((key) => ApplicationDataSourceRegistry.removeDataSource(key));
  Registry.pipeline.unregisterStage(activeRuntimeMetadata.deployStage);
  activeRuntimeMetadata = null;
}

async function initializeOptionalMetadata(message: string, initializer: () => PromiseLike<unknown>): Promise<void> {
  try {
    await initializer();
  } catch (error) {
    console.error(message, error);
  }
}

export function initializeDynamicRuntimeMetadata(): Promise<void> {
  if (!dynamicRuntimeMetadataAttempt) {
    dynamicRuntimeMetadataAttempt = Promise.all([
      initializeDynamicNotificationTypes(),
      initializeOptionalMetadata('Failed to load preconfigured job stage metadata', registerPreconfiguredJobStages),
      initializeOptionalMetadata(
        'Failed to load preconfigured webhook stage metadata',
        registerPreconfiguredWebhookStages,
      ),
    ]).then(() => undefined);
  }
  return dynamicRuntimeMetadataAttempt;
}

export function resetDynamicRuntimeMetadataForTests(): void {
  dynamicRuntimeMetadataAttempt = null;
}

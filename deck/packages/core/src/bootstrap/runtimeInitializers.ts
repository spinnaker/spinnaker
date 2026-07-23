import type { DeckRuntime } from './DeckRuntime';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import type { IStageTypeConfig } from '../domain';
import { registerEntityTagsDataSource } from '../entityTag/entityTags.dataSource';
import { createDirectFunctionReader, registerFunctionDataSource } from '../function/function.dataSource';
import { registerLoadBalancerDataSource } from '../loadBalancer/loadBalancer.dataSource';
import { registerBuiltinNotificationTypes } from '../notification/notification.types';
import { initializeDynamicNotificationTypes } from '../notification/notifications.module';
import { registerDeployStage } from '../pipeline/config/stages/deploy/deployStage';
import { registerPreconfiguredJobStages } from '../pipeline/config/stages/preconfiguredJob/preconfiguredJobStage';
import { registerScriptStage } from '../pipeline/config/stages/script/scriptStage';
import { registerPreconfiguredWebhookStages } from '../pipeline/config/stages/webhook/webhookStage';
import { registerPipelineDataSources } from '../pipeline/pipeline.dataSource';
import { Registry } from '../registry';
import { registerSearchFilterTypes } from '../search/widgets/search.component';
import { registerSecurityGroupDataSource } from '../securityGroup/securityGroup.dataSource';
import { registerServerGroupDataSource } from '../serverGroup/serverGroup.dataSource';
import { registerTaskDataSources } from '../task/task.dataSource';

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
  registerServerGroupDataSource(promiseService, services.clusterService);
  registerLoadBalancerDataSource(promiseService, services.loadBalancerReader);
  registerSecurityGroupDataSource(promiseService, services.securityGroupReader);
  registerFunctionDataSource(
    createDirectFunctionReader(services.providerServiceDelegate),
    <T>(value: T | PromiseLike<T>) => promiseService.when(value),
  );
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

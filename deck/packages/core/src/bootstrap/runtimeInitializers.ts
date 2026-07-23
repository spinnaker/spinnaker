import { registerEntityTagsDataSource } from '../entityTag/entityTags.dataSource';
import { registerFunctionDataSource } from '../function/function.dataSource';
import { registerLoadBalancerDataSource } from '../loadBalancer/loadBalancer.dataSource';
import { registerBuiltinNotificationTypes } from '../notification/notification.types';
import { initializeDynamicNotificationTypes } from '../notification/notifications.module';
import { registerScriptStage } from '../pipeline/config/stages/script/scriptStage';
import { registerPipelineDataSources } from '../pipeline/pipeline.dataSource';
import { registerSearchFilterTypes } from '../search/widgets/search.component';
import { registerSecurityGroupDataSource } from '../securityGroup/securityGroup.dataSource';
import { registerServerGroupDataSource } from '../serverGroup/serverGroup.dataSource';
import { registerTaskDataSources } from '../task/task.dataSource';

export function registerRuntimeDataSources(): void {
  registerServerGroupDataSource();
  registerLoadBalancerDataSource();
  registerSecurityGroupDataSource();
  registerFunctionDataSource();
  registerEntityTagsDataSource();
  registerPipelineDataSources();
  registerTaskDataSources();
}

export function initializeRuntimeMetadata(): void {
  registerRuntimeDataSources();
  registerScriptStage();
  registerBuiltinNotificationTypes();
  registerSearchFilterTypes();
}

export function initializeDynamicRuntimeMetadata(): Promise<void> {
  return initializeDynamicNotificationTypes();
}

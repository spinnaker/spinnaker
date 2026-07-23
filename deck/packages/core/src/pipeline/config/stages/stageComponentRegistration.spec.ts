import { Registry } from '../../../registry';

import './applySourceServerGroupCapacity/applySourceServerGroupCapacityStage.module';
import './bake/bakeStage';
import './cloneServerGroup/cloneServerGroupStage.module';
import './deployService/deployServiceStage';
import './destroyAsg/destroyAsgStage';
import './destroyService/destroyServiceStage';
import './disableAsg/disableAsgStage.module';
import './disableCluster/disableClusterStage';
import './enableAsg/enableAsgStage';
import './executionWindows/executionWindowsStage';
import './findAmi/findAmiStage';
import './findImageFromTags/findImageFromTagsStage';
import './group/groupStage';
import './monitorPipeline/monitorPipelineStage';
import './monitoreddeploy/evaluateHealthStage';
import './monitoreddeploy/notifyDeployStartingStage';
import './resizeAsg/resizeAsgStage';
import './rollbackCluster/rollbackClusterStage';
import { runJobStage } from './runJob/runJobStage';
import './scaleDownCluster/scaleDownClusterStage';
import './shareService/shareServiceStage';
import './shrinkCluster/shrinkClusterStage';
import './tagImage/tagImageStage';
import './unmatchedStageTypeStage/unmatchedStageTypeStage';
import './unshareService/unshareServiceStage';
import './waitForCondition/waitForConditionStage';
import './waitForParentTasks/waitForParentTasks';

const stageKeysThatMustHaveReactConfig = [
  'applySourceServerGroupCapacity',
  'bake',
  'cloneServerGroup',
  'deployService',
  'destroyServerGroup',
  'destroyService',
  'disableServerGroup',
  'disableCluster',
  'enableServerGroup',
  'evaluateDeploymentHealth',
  'findImage',
  'findImageFromTags',
  'group',
  'monitorPipeline',
  'notifyDeployStarting',
  'resizeServerGroup',
  'rollbackCluster',
  'runJob',
  'scaleDownCluster',
  'shareService',
  'shrinkCluster',
  'unmatched',
  'unshareService',
  'upsertImageTags',
  'waitForCondition',
  'waitForRequisiteCompletion',
  'restrictExecutionDuringTimeWindow',
];

const registeredCoreStages = Registry.pipeline.getStageTypes();

describe('stage configs expose React components', () => {
  it('registers direct React config components for every core base and synthetic stage', () => {
    const failures = stageKeysThatMustHaveReactConfig
      .map((key) => {
        const config = registeredCoreStages.find((stageType) => stageType.key === key && !stageType.provides);
        if (!config && key === 'runJob' && runJobStage.component) {
          return null;
        }
        if (!config) {
          return `${key}: not registered directly`;
        }

        const problems = [];
        if (!config.component) {
          problems.push('missing component');
        }
        if ((config as any).templateUrl) {
          problems.push('has templateUrl');
        }
        if ((config as any).controller) {
          problems.push('has controller');
        }
        if ((config as any).controllerAs) {
          problems.push('has controllerAs');
        }
        return problems.length ? `${key}: ${problems.join(', ')}` : null;
      })
      .filter(Boolean);

    expect(failures).toEqual([]);
  });
});

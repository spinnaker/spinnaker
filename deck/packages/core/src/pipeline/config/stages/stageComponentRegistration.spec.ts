import '../../../bootstrap/runtimeInitializers';
import { Registry } from '../../../registry';
import { runJobStage } from './runJob/runJobStage';
import { WaitForParentTasksTransformer } from './waitForParentTasks/waitForParentTasks.transformer';

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
const registeredExecutionTransformers = Registry.pipeline.getExecutionTransformers();

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

  it('registers the wait-for-parent transformer from the production root', () => {
    expect(
      registeredExecutionTransformers.some((transformer) => transformer instanceof WaitForParentTasksTransformer),
    ).toBeTrue();
  });
});

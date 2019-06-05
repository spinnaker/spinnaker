import { CloudfoundryCloneServerGroupStageConfig } from './CloudfoundryCloneServerGroupStageConfig';
import { IStage, Registry } from '@spinnaker/core';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudfoundryCloneServerGroupStageConfig,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  key: 'cloneServerGroup',
  provides: 'cloneServerGroup',
  validators: [],
});

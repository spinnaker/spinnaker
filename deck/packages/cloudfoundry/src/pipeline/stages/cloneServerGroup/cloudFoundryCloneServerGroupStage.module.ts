import type { IStage } from '@spinnaker/core';
import { Registry } from '@spinnaker/core';

import { CloudFoundryCloneServerGroupStageConfig } from './CloudFoundryCloneServerGroupStageConfig';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudFoundryCloneServerGroupStageConfig,
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  key: 'cloneServerGroup',
  provides: 'cloneServerGroup',
  validators: [],
});

import { module } from 'angular';

import { Registry } from 'core/registry';

import { CloneServerGroupExecutionDetails } from './CloneServerGroupExecutionDetails';
import { ExecutionDetailsTasks } from '../common';
import { STAGE_COMMON_MODULE } from '../common/stage.common.module';

export const CLONE_SERVER_GROUP_STAGE = 'spinnaker.core.pipeline.stage.cloneServerGroup';
module(CLONE_SERVER_GROUP_STAGE, [require('../stage.module').name, STAGE_COMMON_MODULE]).config(() => {
  Registry.pipeline.registerStage({
    useBaseProvider: true,
    key: 'cloneServerGroup',
    label: 'Clone Server Group',
    executionDetailsSections: [CloneServerGroupExecutionDetails, ExecutionDetailsTasks],
    description: 'Clones a server group',
    strategy: false,
  });
});

import { module } from 'angular';

import { Registry } from 'core/registry';

import { CloneServerGroupExecutionDetails } from './CloneServerGroupExecutionDetails';
import { ExecutionDetailsTasks } from '../core';
import { STAGE_CORE_MODULE } from '../core/stage.core.module';

export const CLONE_SERVER_GROUP_STAGE = 'spinnaker.core.pipeline.stage.cloneServerGroup';
module(CLONE_SERVER_GROUP_STAGE, [require('../stage.module').name, STAGE_CORE_MODULE]).config(() => {
  Registry.pipeline.registerStage({
    useBaseProvider: true,
    key: 'cloneServerGroup',
    label: 'Clone Server Group',
    executionDetailsSections: [CloneServerGroupExecutionDetails, ExecutionDetailsTasks],
    description: 'Clones a server group',
    strategy: false,
  });
});

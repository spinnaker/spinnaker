import { module } from 'angular';

import { ResizeAsgExecutionDetails } from './ResizeAsgExecutionDetails';
import { ExecutionDetailsTasks } from '../common';
import { Registry } from '../../../../registry';

export const RESIZE_ASG_STAGE = 'spinnaker.core.pipeline.stage.resizeAsgStage';

module(RESIZE_ASG_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    executionDetailsSections: [ResizeAsgExecutionDetails, ExecutionDetailsTasks],
    useBaseProvider: true,
    key: 'resizeServerGroup',
    label: 'Resize Server Group',
    description: 'Resizes a server group',
    strategy: true,
  });
});

import { module } from 'angular';

import { Registry } from '../../../../registry';

export const RESIZE_ASG_STAGE = 'spinnaker.core.pipeline.stage.resizeAsgStage';

module(RESIZE_ASG_STAGE, []).config(() => {
  Registry.pipeline.registerStage({
    executionConfigSections: ['resizeServerGroupConfig', 'taskStatus'],
    executionDetailsUrl: require('./resizeAsgExecutionDetails.html'),
    useBaseProvider: true,
    key: 'resizeServerGroup',
    label: 'Resize Server Group',
    description: 'Resizes a server group',
    strategy: true,
  });
});

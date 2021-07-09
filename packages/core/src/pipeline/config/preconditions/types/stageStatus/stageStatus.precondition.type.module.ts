import { module } from 'angular';

import { STAGE_STATUS_PRECONDITION_CONFIG } from './StageStatusPreconditionConfig';

export const STAGE_STATUS_PRECONDITION = 'spinnaker.core.pipeline.config.preconditions.types.stageStatus';
module(STAGE_STATUS_PRECONDITION, [STAGE_STATUS_PRECONDITION_CONFIG]).config([
  'preconditionTypeConfigProvider',
  function (preconditionTypeConfigProvider: any) {
    preconditionTypeConfigProvider.registerPreconditionType({
      label: 'Stage Status',
      key: 'stageStatus',
      contextTemplateUrl: require('./additionalFields.html'),
    });
  },
]);

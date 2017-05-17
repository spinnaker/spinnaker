import { module } from 'angular';

import { ACCOUNT_SERVICE, NAMING_SERVICE } from '@spinnaker/core';

import { ISOLATED_TESTING_TARGET_STAGE } from './isolatedTestingTargetStage';

export const ISOLATED_TESTING_TARGET_STAGE_MODULE = 'spinnaker.netflix.pipeline.stage.isolatedTestingTarget';

module(ISOLATED_TESTING_TARGET_STAGE_MODULE, [
  ISOLATED_TESTING_TARGET_STAGE,
  ACCOUNT_SERVICE,
  NAMING_SERVICE,
]);

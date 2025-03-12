import type { IStage } from '@spinnaker/core';
import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import {
  CloudFoundryDestroyServiceStageConfig,
  validateCloudFoundryDestroyServiceStage,
} from './CloudFoundryDestroyServiceStageConfig';
import { CloudFoundryServiceExecutionDetails } from '../../../presentation';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudFoundryDestroyServiceStageConfig,
  supportsCustomTimeout: true,
  executionDetailsSections: [CloudFoundryServiceExecutionDetails, ExecutionDetailsTasks],
  key: 'destroyService',
  provides: 'destroyService',
  validateFn: validateCloudFoundryDestroyServiceStage,
});

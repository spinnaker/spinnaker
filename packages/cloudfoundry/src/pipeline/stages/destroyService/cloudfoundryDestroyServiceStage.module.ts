import { ExecutionDetailsTasks, IStage, Registry } from '@spinnaker/core';

import {
  CloudFoundryDestroyServiceStageConfig,
  validateCloudFoundryDestroyServiceStage,
} from './CloudfoundryDestroyServiceStageConfig';
import { CloudfoundryServiceExecutionDetails } from '../../../presentation';

Registry.pipeline.registerStage({
  accountExtractor: (stage: IStage) => [stage.context.credentials],
  configAccountExtractor: (stage: IStage) => [stage.credentials],
  cloudProvider: 'cloudfoundry',
  component: CloudFoundryDestroyServiceStageConfig,
  supportsCustomTimeout: true,
  executionDetailsSections: [CloudfoundryServiceExecutionDetails, ExecutionDetailsTasks],
  key: 'destroyService',
  provides: 'destroyService',
  validateFn: validateCloudFoundryDestroyServiceStage,
});

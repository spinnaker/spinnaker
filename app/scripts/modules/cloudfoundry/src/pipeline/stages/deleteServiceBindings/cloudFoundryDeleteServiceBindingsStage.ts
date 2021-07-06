import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import {
  CloudFoundryDeleteServiceBindingsConfig,
  validateCloudFoundryDeleteServiceBindingsStage,
} from './CloudFoundryDeleteServiceBindingsConfig';

export const CF_DELETE_SERVICE_BINDINGS_STAGE_KEY = 'cloudFoundryDeleteServiceBindings';

Registry.pipeline.registerStage({
  label: 'Delete Service Bindings',
  description: 'Delete CF service bindings with optional parameters.',
  key: CF_DELETE_SERVICE_BINDINGS_STAGE_KEY,
  component: CloudFoundryDeleteServiceBindingsConfig,
  producesArtifacts: false,
  cloudProvider: 'cloudfoundry',
  executionDetailsSections: [ExecutionDetailsTasks],
  validateFn: validateCloudFoundryDeleteServiceBindingsStage,
});

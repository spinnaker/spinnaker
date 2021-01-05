import { ExecutionDetailsTasks, Registry } from '@spinnaker/core';

import {
  CloudFoundryCreateServiceBindingsConfig,
  validateCloudFoundryCreateServiceBindingsStage,
} from './CloudFoundryCreateServiceBindingsConfig';

export const CF_CREATE_SERVICE_BINDINGS_STAGE_KEY = 'cloudFoundryCreateServiceBindings';

Registry.pipeline.registerStage({
  label: 'Create Service Bindings',
  description: 'Create CF service bindings with optional parameters.',
  key: CF_CREATE_SERVICE_BINDINGS_STAGE_KEY,
  component: CloudFoundryCreateServiceBindingsConfig,
  producesArtifacts: false,
  cloudProvider: 'cloudfoundry',
  executionDetailsSections: [ExecutionDetailsTasks],
  validateFn: validateCloudFoundryCreateServiceBindingsStage,
});

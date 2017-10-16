import { module } from 'angular';

import { EXPECTED_ARTIFACT } from './expectedArtifact.component';
import { CUSTOM_ARTIFACT } from './custom/custom.artifact';
import { GCS_ARTIFACT } from './gcs/gcs.artifact';
import { ARTIFACT } from './artifact.component';

export const ARTIFACT_MODULE = 'spinnaker.core.pipeline.config.trigger.artifacts';

module(ARTIFACT_MODULE, [
  EXPECTED_ARTIFACT,
  CUSTOM_ARTIFACT,
  GCS_ARTIFACT,
  ARTIFACT,
]);

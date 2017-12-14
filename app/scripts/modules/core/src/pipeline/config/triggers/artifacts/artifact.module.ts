import { module } from 'angular';

import { EXPECTED_ARTIFACT } from './expectedArtifact.component';
import { CUSTOM_ARTIFACT } from './custom/custom.artifact';
import { GCS_ARTIFACT } from './gcs/gcs.artifact';
import { DOCKER_ARTIFACT } from './docker/docker.artifact';
import { ARTIFACT } from './artifact.component';
import { GITHUB_ARTIFACT } from 'core/pipeline/config/triggers/artifacts/github/github.artifact';

export const ARTIFACT_MODULE = 'spinnaker.core.pipeline.config.trigger.artifacts';

module(ARTIFACT_MODULE, [
  EXPECTED_ARTIFACT,
  CUSTOM_ARTIFACT,
  GCS_ARTIFACT,
  GITHUB_ARTIFACT,
  DOCKER_ARTIFACT,
  ARTIFACT,
]);

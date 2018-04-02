import { module } from 'angular';

import { EXPECTED_ARTIFACT } from './expectedArtifact.component';
import { CUSTOM_ARTIFACT } from './custom/custom.artifact';
import { GCS_ARTIFACT } from './gcs/gcs.artifact';
import { DOCKER_ARTIFACT } from './docker/docker.artifact';
import { DEFAULT_DOCKER_ARTIFACT } from './docker/defaultDocker.artifact';
import { DEFAULT_GCS_ARTIFACT } from './gcs/defaultGcs.artifact';
import { DEFAULT_GITHUB_ARTIFACT } from './github/defaultGithub.artifact';
import { ARTIFACT } from './artifact.component';
import { GITHUB_ARTIFACT } from 'core/pipeline/config/triggers/artifacts/github/github.artifact';
import { BASE64_ARTIFACT } from 'core/pipeline/config/triggers/artifacts/base64/base64.artifact';
import { DEFAULT_BASE64_ARTIFACT } from 'core/pipeline/config/triggers/artifacts/base64/defaultBase64.artifact';
import { S3_ARTIFACT } from 'core/pipeline/config/triggers/artifacts/s3/s3.artifact';
import { DEFAULT_S3_ARTIFACT } from 'core/pipeline/config/triggers/artifacts/s3/defaultS3.artifact';

export const ARTIFACT_MODULE = 'spinnaker.core.pipeline.config.trigger.artifacts';

module(ARTIFACT_MODULE, [
  EXPECTED_ARTIFACT,
  CUSTOM_ARTIFACT,
  GCS_ARTIFACT,
  GITHUB_ARTIFACT,
  DOCKER_ARTIFACT,
  BASE64_ARTIFACT,
  S3_ARTIFACT,
  DEFAULT_S3_ARTIFACT,
  DEFAULT_DOCKER_ARTIFACT,
  DEFAULT_GCS_ARTIFACT,
  DEFAULT_GITHUB_ARTIFACT,
  DEFAULT_BASE64_ARTIFACT,
  ARTIFACT,
]);

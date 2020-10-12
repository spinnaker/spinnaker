export interface IArtifactTypePatterns {
  [typeName: string]: RegExp;
}

export const ArtifactTypePatterns: IArtifactTypePatterns = {
  BITBUCKET_FILE: /bitbucket\/file/,
  CUSTOM_OBJECT: /custom\/object/,
  DOCKER_IMAGE: /docker\/image/,
  EMBEDDED_BASE64: /embedded\/base64/,
  GCS_OBJECT: /gcs\/object/,
  GITHUB_FILE: /github\/file/,
  GIT_REPO: /git\/repo/,
  GITLAB_FILE: /gitlab\/file/,
  GCE_MACHINE_IMAGE: /gce\/image/,
  JENKINS_FILE: /jenkins\/file/,
  KUBERNETES: /kubernetes\/(.*)/,
  S3_OBJECT: /s3\/object/,
  HELM_CHART: /helm\/chart/,
  IVY_FILE: /ivy\/file/,
  MAVEN_FILE: /maven\/file/,
  HTTP_FILE: /http\/file/,
  FRONT50_PIPELINE_TEMPLATE: /front50\/pipelineTemplate/,
  ORACLE_OBJECT: /oracle\/object/,
};

export const excludeAllTypesExcept = (...types: RegExp[]) =>
  Object.keys(ArtifactTypePatterns)
    .map((k) => ArtifactTypePatterns[k])
    .filter((type) => !types.includes(type));

import { ArtifactTypePatterns } from './ArtifactTypes';

const unknownArtifactPath = require('./icons/unknown-type-artifact.svg');

interface IArtifactIcon {
  type: RegExp;
  path: string;
}

export class ArtifactIconService {
  private static icons: IArtifactIcon[] = [];

  public static registerType(type: RegExp, path: string) {
    ArtifactIconService.icons.push({ type, path });
  }

  public static getPath(type: string) {
    if (type == null) {
      return unknownArtifactPath;
    }
    const icon = ArtifactIconService.icons.find(entry => entry.type.test(type));
    if (icon === undefined) {
      return null;
    }
    return icon.path;
  }
}

ArtifactIconService.registerType(ArtifactTypePatterns.DOCKER_IMAGE, require('./icons/docker-image-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.KUBERNETES, require('./icons/kubernetes-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.EMBEDDED_BASE64, require('./icons/embedded-base64-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.GCS_OBJECT, require('./icons/gcs-file-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.GITHUB_FILE, require('./icons/github-file-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.GITLAB_FILE, require('./icons/gitlab-file-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.BITBUCKET_FILE, require('./icons/bitbucket-file-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.S3_OBJECT, require('./icons/s3-object-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.HELM_CHART, require('./icons/helm-chart-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.IVY_FILE, require('./icons/ivy-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.JENKINS_FILE, require('./icons/jenkins-file-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.MAVEN_FILE, require('./icons/maven-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypePatterns.HTTP_FILE, require('./icons/http-artifact.svg'));

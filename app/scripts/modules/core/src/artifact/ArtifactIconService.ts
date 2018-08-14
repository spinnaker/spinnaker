import * as ArtifactTypes from './ArtifactTypes';

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

ArtifactIconService.registerType(ArtifactTypes.DOCKER_IMAGE, require('./icons/docker-image-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypes.KUBERNETES, require('./icons/kubernetes-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypes.EMBEDDED_BASE64, require('./icons/embedded-base64-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypes.GCS_OBJECT, require('./icons/gcs-file-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypes.GITHUB_FILE, require('./icons/github-file-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypes.GITLAB_FILE, require('./icons/gitlab-file-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypes.BITBUCKET_FILE, require('./icons/bitbucket-file-artifact.svg'));
ArtifactIconService.registerType(ArtifactTypes.S3_OBJECT, require('./icons/s3-object-artifact.svg'));

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

ArtifactIconService.registerType(/docker\/image/, require('./icons/docker-image-artifact.svg'));
ArtifactIconService.registerType(/kubernetes\/.*/, require('./icons/kubernetes-artifact.svg'));
ArtifactIconService.registerType(/embedded\/base64/, require('./icons/embedded-base64-artifact.svg'));
ArtifactIconService.registerType(/gcs\/object/, require('./icons/gcs-file-artifact.svg'));
ArtifactIconService.registerType(/github\/file/, require('./icons/github-file-artifact.svg'));
ArtifactIconService.registerType(/gitlab\/file/, require('./icons/gitlab-file-artifact.svg'));
ArtifactIconService.registerType(/bitbucket\/file/, require('./icons/bitbucket-file-artifact.svg'));
ArtifactIconService.registerType(/s3\/object/, require('./icons/s3-object-artifact.svg'));

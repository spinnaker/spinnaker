import { ArtifactTypePatterns } from './ArtifactTypes';
import bitbucketFileIcon from './icons/bitbucket-file-artifact.svg';
import dockerIcon from './icons/docker-image-artifact.svg';
import embeddedBase64Icon from './icons/embedded-base64-artifact.svg';
import gcsObjectIcon from './icons/gcs-file-artifact.svg';
import gitRepoIcon from './icons/git-repo-artifact.svg';
import gitHubFileIcon from './icons/github-file-artifact.svg';
import gitLabFileIcon from './icons/gitlab-file-artifact.svg';
import helmChartIcon from './icons/helm-chart-artifact.svg';
import httpFileIcon from './icons/http-artifact.svg';
import ivyFileIcon from './icons/ivy-artifact.svg';
import jenkinsFileIcon from './icons/jenkins-file-artifact.svg';
import kubernetesIcon from './icons/kubernetes-artifact.svg';
import mavenFileIcon from './icons/maven-artifact.svg';
import oracleObjectIcon from './icons/oracle-object-artifact.svg';
import s3ObjectIcon from './icons/s3-object-artifact.svg';
import unknownArtifactIcon from './icons/unknown-type-artifact.svg';

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
      return unknownArtifactIcon;
    }
    const icon = ArtifactIconService.icons.find((entry) => entry.type.test(type));
    if (icon === undefined) {
      return null;
    }
    return icon.path;
  }
}

ArtifactIconService.registerType(ArtifactTypePatterns.CUSTOM_OBJECT, unknownArtifactIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.DOCKER_IMAGE, dockerIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.KUBERNETES, kubernetesIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.EMBEDDED_BASE64, embeddedBase64Icon);
ArtifactIconService.registerType(ArtifactTypePatterns.GCS_OBJECT, gcsObjectIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.GITHUB_FILE, gitHubFileIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.GIT_REPO, gitRepoIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.GITLAB_FILE, gitLabFileIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.BITBUCKET_FILE, bitbucketFileIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.S3_OBJECT, s3ObjectIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.HELM_CHART, helmChartIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.IVY_FILE, ivyFileIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.JENKINS_FILE, jenkinsFileIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.MAVEN_FILE, mavenFileIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.HTTP_FILE, httpFileIcon);
ArtifactIconService.registerType(ArtifactTypePatterns.ORACLE_OBJECT, oracleObjectIcon);

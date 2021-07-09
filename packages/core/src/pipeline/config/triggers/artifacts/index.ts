import { Base64Default, Base64Match } from './base64/Base64ArtifactEditor';
import { BitbucketDefault, BitbucketMatch } from './bitbucket/BitbucketArtifactEditor';
import { CustomDefault, CustomMatch } from './custom/CustomArtifactEditor';
import { DockerDefault, DockerMatch } from './docker/DockerArtifactEditor';
import { IArtifactKindConfig } from '../../../../domain';
import { GcsDefault, GcsMatch } from './gcs/GcsArtifactEditor';
import { GithubDefault, GithubMatch } from './github/GithubArtifactEditor';
import { GitlabDefault, GitlabMatch } from './gitlab/GitlabArtifactEditor';
import { GitRepoDefault, GitRepoMatch } from './gitrepo/GitRepoArtifactEditor';
import { HelmDefault, HelmMatch } from './helm/HelmArtifactEditor';
import { HttpDefault, HttpMatch } from './http/HttpArtifactEditor';
import { IvyDefault, IvyMatch } from './ivy/IvyArtifactEditor';
import { JenkinsDefault, JenkinsMatch } from './jenkins/JenkinsArtifactEditor';
import { KubernetesDefault, KubernetesMatch } from './kubernetes/KubernetesArtifactEditor';
import { MavenDefault, MavenMatch } from './maven/MavenArtifactEditor';
import { OracleDefault, OracleMatch } from './oracle/OracleArtifactEditor';
import { S3Default, S3Match } from './s3/S3ArtifactEditor';

export const artifactKindConfigs: IArtifactKindConfig[] = [
  Base64Match,
  Base64Default,
  BitbucketMatch,
  BitbucketDefault,
  CustomMatch,
  CustomDefault,
  DockerMatch,
  DockerDefault,
  GcsMatch,
  GcsDefault,
  GithubMatch,
  GithubDefault,
  GitRepoMatch,
  GitRepoDefault,
  GitlabMatch,
  GitlabDefault,
  HelmMatch,
  HelmDefault,
  HttpMatch,
  HttpDefault,
  IvyMatch,
  IvyDefault,
  JenkinsMatch,
  JenkinsDefault,
  MavenMatch,
  MavenDefault,
  S3Match,
  S3Default,
  OracleMatch,
  OracleDefault,
  KubernetesMatch,
  KubernetesDefault,
];

export * from './TriggerArtifactConstraintSelectorInput';

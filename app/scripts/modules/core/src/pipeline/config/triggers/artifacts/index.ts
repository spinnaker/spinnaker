import { IArtifactKindConfig } from 'core/domain';

import { Base64Match, Base64Default } from './base64/Base64ArtifactEditor';
import { BitbucketMatch, BitbucketDefault } from './bitbucket/BitbucketArtifactEditor';
import { CustomMatch, CustomDefault } from './custom/CustomArtifactEditor';
import { DockerMatch, DockerDefault } from './docker/DockerArtifactEditor';
import { GcsMatch, GcsDefault } from './gcs/GcsArtifactEditor';
import { GithubMatch, GithubDefault } from './github/GithubArtifactEditor';
import { GitRepoMatch, GitRepoDefault } from './gitrepo/GitRepoArtifactEditor';
import { GitlabMatch, GitlabDefault } from './gitlab/GitlabArtifactEditor';
import { HelmMatch, HelmDefault } from './helm/HelmArtifactEditor';
import { HttpMatch, HttpDefault } from './http/HttpArtifactEditor';
import { IvyMatch, IvyDefault } from './ivy/IvyArtifactEditor';
import { JenkinsMatch, JenkinsDefault } from './jenkins/JenkinsArtifactEditor';
import { KubernetesMatch, KubernetesDefault } from './kubernetes/KubernetesArtifactEditor';
import { MavenMatch, MavenDefault } from './maven/MavenArtifactEditor';
import { S3Match, S3Default } from './s3/S3ArtifactEditor';
import { OracleMatch, OracleDefault } from './oracle/OracleArtifactEditor';

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

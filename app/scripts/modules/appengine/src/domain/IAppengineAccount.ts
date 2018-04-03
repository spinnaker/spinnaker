import { IAccountDetails } from '@spinnaker/core';

export interface IAppengineAccount extends IAccountDetails {
  region: string;
  supportedGitCredentialTypes: GitCredentialType[];
}

export type GitCredentialType = 'NONE' | 'HTTPS_USERNAME_PASSWORD' | 'HTTPS_GITHUB_OAUTH_TOKEN' | 'SSH';

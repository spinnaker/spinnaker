import { ICredentials } from '@spinnaker/core';

export interface ITitusCredentials extends ICredentials {
  awsAccount: string;
  awsVpc: string;
}

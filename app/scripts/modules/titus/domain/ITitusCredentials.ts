import {ICredentials} from 'core/domain/ICredentials';

export interface ITitusCredentials extends ICredentials {
  awsAccount: string;
  awsVpc: string;
}

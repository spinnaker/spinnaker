import {IAccountDetails} from 'core/account/account.service';

export interface IAppengineAccount extends IAccountDetails {
  region: string;
}

import { REST } from '../api/ApiService';
import { SETTINGS } from '../config/settings';

export class ServiceAccountReader {
  public static getServiceAccounts(): PromiseLike<string[]> {
    if (!SETTINGS.feature.fiatEnabled) {
      return Promise.resolve([]);
    } else {
      return REST('/auth/user/serviceAccounts').get();
    }
  }

  public static getServiceAccountsForApplication(application: string): PromiseLike<string[]> {
    if (!SETTINGS.feature.fiatEnabled) {
      return Promise.resolve([]);
    } else {
      return REST('/auth/user/serviceAccounts').query({ application: application }).get();
    }
  }
}

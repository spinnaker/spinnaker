import { $q } from 'ngimport';

import { REST } from 'core/api/ApiService';
import { SETTINGS } from 'core/config/settings';

export class ServiceAccountReader {
  public static getServiceAccounts(): PromiseLike<string[]> {
    if (!SETTINGS.feature.fiatEnabled) {
      return $q.resolve([]);
    } else {
      return REST().path('auth', 'user', 'serviceAccounts').get();
    }
  }

  public static getServiceAccountsForApplication(application: string): PromiseLike<string[]> {
    if (!SETTINGS.feature.fiatEnabled) {
      return $q.resolve([]);
    } else {
      return REST().path('auth', 'user', 'serviceAccounts').query({ application: application }).get();
    }
  }
}

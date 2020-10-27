

import { $q } from 'ngimport';

import { API } from 'core/api/ApiService';
import { SETTINGS } from 'core/config/settings';

export class ServiceAccountReader {
  public static getServiceAccounts(): PromiseLike<string[]> {
    if (!SETTINGS.feature.fiatEnabled) {
      return $q.resolve([]);
    } else {
      return API.one('auth').one('user').one('serviceAccounts').get();
    }
  }

  public static getServiceAccountsForApplication(application: string): PromiseLike<string[]> {
    if (!SETTINGS.feature.fiatEnabled) {
      return $q.resolve([]);
    } else {
      return API.one('auth').one('user').one('serviceAccounts').withParams({ application: application }).get();
    }
  }
}

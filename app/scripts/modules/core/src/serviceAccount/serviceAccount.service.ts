import { module } from 'angular';

import { API } from 'core/api/ApiService';
import { SETTINGS } from 'core/config/settings';

export class ServiceAccountService {
  constructor(private $q: ng.IQService) {
    'ngInject';
  }

  public getServiceAccounts(): ng.IPromise<string[]> {
    if (!SETTINGS.feature.fiatEnabled) {
      return this.$q.resolve([]);
    } else {
      return API.one('auth')
        .one('user')
        .one('serviceAccounts')
        .get();
    }
  }
}

export const SERVICE_ACCOUNT_SERVICE = 'spinnaker.core.serviceAccount.service';
module(SERVICE_ACCOUNT_SERVICE, []).service('serviceAccountService', ServiceAccountService);

import {module} from 'angular';

import {API_SERVICE, Api} from 'core/api/api.service';
import {SETTINGS} from 'core/config/settings';

export class ServiceAccountService {

  static get $inject() { return ['$q', 'API']; }

  constructor(private $q: ng.IQService, private API: Api) {}

  public getServiceAccounts(): ng.IPromise<string[]> {
    if (!SETTINGS.feature.fiatEnabled) {
      return this.$q.resolve([]);
    } else {
      return this.API.one('auth').one('user').one('serviceAccounts').get();
    }
  }
}

export const SERVICE_ACCOUNT_SERVICE = 'spinnaker.core.serviceAccount.service';
module(SERVICE_ACCOUNT_SERVICE, [
  API_SERVICE
]).service('serviceAccountService', ServiceAccountService);

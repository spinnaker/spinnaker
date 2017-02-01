import {module} from 'angular';
import {API_SERVICE, Api} from 'core/api/api.service';

export class ServiceAccountService {

  static get $inject() { return ['$q', 'API', 'settings']; }

  constructor(private $q: ng.IQService, private API: Api, private settings: any) {}

  public getServiceAccounts(): ng.IPromise<string[]> {
    if (!this.settings.feature.fiatEnabled) {
      return this.$q.resolve([]);
    } else {
      return this.API.one('auth').one('user').one('serviceAccounts').get();
    }
  }
}

export const SERVICE_ACCOUNT_SERVICE = 'spinnaker.core.serviceAccount.service';
module(SERVICE_ACCOUNT_SERVICE, [
  API_SERVICE,
  require('../config/settings.js'),
]).service('serviceAccountService', ServiceAccountService);

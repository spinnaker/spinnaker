import * as _ from 'lodash';

import {ACCOUNT_SERVICE, AccountService} from 'core/account/account.service';
import {API_SERVICE, Api} from 'core/api/api.service';

interface IBaseImage {
  id: string;
  shortDescription: string;
  detailedDescription: string;
  packageType: string;
}

interface IBaseOsOptions {
  cloudProvider: string;
  baseImages: IBaseImage[];
}

export class BakeryService {

  static get $inject() { return ['$q', 'API', 'accountService', 'settings']; }

  public constructor(private $q: ng.IQService, private API: Api, private accountService: AccountService,
                     private settings: any) {}

  public getRegions(provider: string): ng.IPromise<string[]> {
    if (_.has(this.settings, `providers.${provider}.bakeryRegions`)) {
      return this.$q.when(_.get(this.settings, `providers.${provider}.bakeryRegions`));
    }
    return this.accountService.getUniqueAttributeForAllAccounts(provider, 'regions')
      .then((regions: string[]) => regions.sort());
  }

  public getBaseOsOptions(provider: string): ng.IPromise<IBaseOsOptions> {
    return this.getAllBaseOsOptions().then(options => {
      return options.find(o => o.cloudProvider === provider);
    });
  }

  private getAllBaseOsOptions(): ng.IPromise<IBaseOsOptions[]> {
    return this.API.one('bakery', 'options').useCache().getList();
  }

  public getBaseLabelOptions(): ng.IPromise<string[]> {
    return this.$q.when(['release', 'candidate', 'previous', 'unstable']);
  }

  public getVmTypes(): ng.IPromise<string[]> {
    return this.$q.when(['hvm', 'pv']);
  }

  public getStoreTypes(): ng.IPromise<string[]> {
    return this.$q.when(['ebs', 's3', 'docker']);
  }
}

export const BAKERY_SERVICE = 'spinnaker.core.pipeline.bakery.service';

angular.module(BAKERY_SERVICE, [
  API_SERVICE,
  ACCOUNT_SERVICE,
  require('core/config/settings'),
]).service('bakeryService', BakeryService);

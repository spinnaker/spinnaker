import { get, has } from 'lodash';
import { IPromise } from 'angular';
import { $q } from 'ngimport';

import { AccountService } from 'core/account/AccountService';
import { API } from 'core/api/ApiService';
import { SETTINGS } from 'core/config/settings';

export interface IBaseImage {
  id: string;
  shortDescription: string;
  detailedDescription: string;
  packageType: string;
  displayName: string;
}

export interface IBaseOsOptions {
  cloudProvider: string;
  baseImages: IBaseImage[];
}

export class BakeryReader {
  public static getRegions(provider: string): IPromise<string[]> {
    if (has(SETTINGS, `providers.${provider}.bakeryRegions`)) {
      return $q.when(get(SETTINGS, `providers.${provider}.bakeryRegions`));
    }
    return AccountService.getUniqueAttributeForAllAccounts(provider, 'regions').then((regions: string[]) =>
      regions.sort(),
    );
  }

  public static getBaseOsOptions(provider: string): ng.IPromise<IBaseOsOptions> {
    return this.getAllBaseOsOptions().then((options) => {
      return options.find((o) => o.cloudProvider === provider);
    });
  }

  private static getAllBaseOsOptions(): ng.IPromise<IBaseOsOptions[]> {
    return API.one('bakery', 'options').useCache().getList();
  }

  public static getBaseLabelOptions(): ng.IPromise<string[]> {
    return $q.when(['release', 'candidate', 'previous', 'unstable']);
  }
}

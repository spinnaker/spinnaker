import { get, has } from 'lodash';
import { $q } from 'ngimport';

import { AccountService } from '../../../../account/AccountService';
import { REST } from '../../../../api/ApiService';
import { SETTINGS } from '../../../../config/settings';

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
  public static getRegions(provider: string): PromiseLike<string[]> {
    if (has(SETTINGS, `providers.${provider}.bakeryRegions`)) {
      return $q.when(get(SETTINGS, `providers.${provider}.bakeryRegions`));
    }
    return AccountService.getUniqueAttributeForAllAccounts(provider, 'regions').then((regions: string[]) =>
      regions.sort(),
    );
  }

  public static getBaseOsOptions(provider: string): PromiseLike<IBaseOsOptions> {
    return this.getAllBaseOsOptions().then((options) => {
      return options.find((o) => o.cloudProvider === provider);
    });
  }

  private static getAllBaseOsOptions(): PromiseLike<IBaseOsOptions[]> {
    return REST('/bakery/options').useCache().get();
  }

  public static getBaseLabelOptions(): PromiseLike<string[]> {
    return $q.when(['release', 'candidate', 'previous', 'unstable']);
  }
}

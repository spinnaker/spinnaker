import { uniq } from 'lodash';
import { $q } from 'ngimport';
import { IPromise } from 'angular';

import { AccountService, IAccountDetails } from 'core/account';
import { Application } from 'core/application';
import { CloudProviderRegistry, ICloudProviderConfig } from '../CloudProviderRegistry';
import { SETTINGS } from 'core/config';
import { ProviderSelectionModal } from './ProviderSelectionModal';

export type IProviderSelectionFilter = (app: Application, acc: IAccountDetails, prov: ICloudProviderConfig) => boolean;

export class ProviderSelectionService {
  public static selectProvider(
    application: Application,
    feature: string,
    filterFn?: IProviderSelectionFilter,
  ): IPromise<string> {
    return AccountService.applicationAccounts(application).then((accounts: IAccountDetails[]) => {
      let reducedAccounts: IAccountDetails[] = [];
      if (feature) {
        reducedAccounts = accounts.filter(a => CloudProviderRegistry.hasValue(a.cloudProvider, feature));
      }

      if (filterFn) {
        reducedAccounts = reducedAccounts.filter((acc: IAccountDetails) => {
          return filterFn(application, acc, CloudProviderRegistry.getProvider(acc.cloudProvider, acc.skin));
        });
      }

      // reduce the accounts to the smallest, unique collection taking into consideration the useProvider values
      const providerOptions = uniq(
        reducedAccounts.map(a => {
          const providerFeature = CloudProviderRegistry.getProvider(a.cloudProvider)[feature] || {};
          return providerFeature.useProvider || a.cloudProvider;
        }),
      );

      let provider;
      if (providerOptions.length > 1) {
        return ProviderSelectionModal.show({ providerOptions });
      } else if (providerOptions.length === 1) {
        provider = $q.when(providerOptions[0]);
      } else {
        provider = $q.when(SETTINGS.defaultProvider || 'aws');
      }
      return provider;
    });
  }
}

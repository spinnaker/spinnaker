import { uniq } from 'lodash';

import type { ICloudProviderConfig } from '../CloudProviderRegistry';
import { CloudProviderRegistry } from '../CloudProviderRegistry';
import { ProviderSelectionModal } from './ProviderSelectionModal';
import type { IAccountDetails } from '../../account';
import { AccountService } from '../../account';
import { AngularServices } from '../../angular/services';
import type { Application } from '../../application';
import { SETTINGS } from '../../config';

export type IProviderSelectionFilter = (app: Application, acc: IAccountDetails, prov: ICloudProviderConfig) => boolean;

interface IProviderOption {
  account: IAccountDetails;
  provider: string;
  providerConfig: ICloudProviderConfig;
}

export class ProviderSelectionService {
  public static selectProvider(
    application: Application,
    feature: string,
    filterFn?: IProviderSelectionFilter,
  ): PromiseLike<string> {
    return AccountService.applicationAccounts(application).then((accounts: IAccountDetails[]) => {
      let reducedAccounts: IAccountDetails[] = [];
      if (feature) {
        reducedAccounts = accounts.filter((a) => CloudProviderRegistry.hasValue(a.cloudProvider, feature));
      }

      const effectiveProviderOptions: IProviderOption[] = reducedAccounts.reduce((options, account) => {
        const accountProvider = CloudProviderRegistry.getProvider(account.cloudProvider);
        if (!accountProvider) {
          return options;
        }
        const providerFeature = accountProvider[feature] || {};
        const provider = providerFeature.useProvider || account.cloudProvider;
        const providerConfig = CloudProviderRegistry.getProvider(provider);
        if (providerConfig) {
          options.push({ account, provider, providerConfig });
        } else if (!providerFeature.useProvider) {
          options.push({ account, provider: account.cloudProvider, providerConfig: accountProvider });
        }
        return options;
      }, [] as IProviderOption[]);

      const filteredProviderOptions = filterFn
        ? effectiveProviderOptions.filter((option) => filterFn(application, option.account, option.providerConfig))
        : effectiveProviderOptions;

      // reduce the accounts to the smallest, unique collection taking into consideration the useProvider values
      const providerOptions = uniq(
        filteredProviderOptions
          .filter((option) => {
            return !CloudProviderRegistry.isDisabled(option.provider);
          })
          .map((option) => option.provider),
      );

      const $q = AngularServices.$q;
      let provider;
      if (providerOptions.length > 1) {
        return ProviderSelectionModal.show({ providerOptions });
      } else if (providerOptions.length === 1) {
        provider = $q.when(providerOptions[0]);
      } else if (filterFn) {
        provider = $q.reject(new Error(`No providers support ${feature} for this action.`));
      } else {
        provider = $q.when(SETTINGS.defaultProvider || 'aws');
      }
      return provider;
    });
  }

  public static isDisabled(app: Application): PromiseLike<boolean> {
    return AccountService.applicationAccounts(app).then((accounts: IAccountDetails[]) => {
      let isDisable = false;
      const cloudProvidersEnabled = accounts.filter((a) => {
        return !CloudProviderRegistry.isDisabled(a.cloudProvider);
      });

      if (cloudProvidersEnabled.length === 0) {
        isDisable = true;
      }
      return isDisable;
    });
  }
}

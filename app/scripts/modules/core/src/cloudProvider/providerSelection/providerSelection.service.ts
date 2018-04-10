import { IModalService } from 'angular-ui-bootstrap';
import { IPromise, module, IQService } from 'angular';
import { uniq } from 'lodash';

import { IAccountDetails, ACCOUNT_SERVICE, AccountService } from 'core/account/account.service';
import { Application } from 'core/application/application.model';
import {
  CLOUD_PROVIDER_REGISTRY,
  CloudProviderRegistry,
  ICloudProviderConfig,
} from 'core/cloudProvider/cloudProvider.registry';
import { SETTINGS } from 'core/config/settings';

export type IProviderSelectionFilter = (app: Application, acc: IAccountDetails, prov: ICloudProviderConfig) => boolean;

export class ProviderSelectionService {
  constructor(
    private $uibModal: IModalService,
    private $q: IQService,
    private accountService: AccountService,
    private cloudProviderRegistry: CloudProviderRegistry,
  ) {
    'ngInject';
  }

  public selectProvider(
    application: Application,
    feature: string,
    filterFn?: IProviderSelectionFilter,
  ): IPromise<string> {
    return this.accountService.applicationAccounts(application).then((accounts: IAccountDetails[]) => {
      let reducedAccounts: IAccountDetails[] = [];
      if (feature) {
        reducedAccounts = accounts.filter(a => this.cloudProviderRegistry.hasValue(a.cloudProvider, feature));
      }

      if (filterFn) {
        reducedAccounts = reducedAccounts.filter((acc: IAccountDetails) => {
          return filterFn(application, acc, this.cloudProviderRegistry.getProvider(acc.cloudProvider, acc.skin));
        });
      }

      // reduce the accounts to the smallest, unique collection taking into consideration the useProvider values
      const reducedProviders = uniq(
        reducedAccounts.map(a => {
          const providerFeature = this.cloudProviderRegistry.getProvider(a.cloudProvider)[feature] || {};
          return providerFeature.useProvider || a.cloudProvider;
        }),
      );

      let provider;
      if (reducedProviders.length > 1) {
        provider = this.$uibModal.open({
          templateUrl: require('./providerSelection.html'),
          controller: 'ProviderSelectCtrl as ctrl',
          resolve: {
            providerOptions: () => reducedProviders,
          },
        }).result;
      } else if (reducedProviders.length === 1) {
        provider = this.$q.when(reducedProviders[0]);
      } else {
        provider = this.$q.when(SETTINGS.defaultProvider || 'aws');
      }
      return provider;
    });
  }
}

export const PROVIDER_SELECTION_SERVICE = 'spinnaker.cloudProvider.providerSelection.service';
module(PROVIDER_SELECTION_SERVICE, [ACCOUNT_SERVICE, CLOUD_PROVIDER_REGISTRY]).service(
  'providerSelectionService',
  ProviderSelectionService,
);

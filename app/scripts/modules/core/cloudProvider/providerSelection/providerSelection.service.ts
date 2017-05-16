import { IModalService } from 'angular-ui-bootstrap';
import { IPromise, module, IQService } from 'angular';
import { uniq } from 'lodash';

import { ACCOUNT_SERVICE, AccountService } from 'core/account/account.service';
import { Application } from 'core/application/application.model';
import { CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry } from 'core/cloudProvider/cloudProvider.registry';
import { SETTINGS } from 'core/config/settings';

export class ProviderSelectionService {
  constructor(private $uibModal: IModalService,
              private $q: IQService,
              private accountService: AccountService,
              private cloudProviderRegistry: CloudProviderRegistry) {
    'ngInject';
  }

  public selectProvider(application: Application, feature: string): IPromise<string> {
    return this.accountService.listProviders(application).then((providers) => {
      let provider;
      let reducedProviders: string[] = [];
      if (feature) {
        reducedProviders = providers.filter((p) => this.cloudProviderRegistry.hasValue(p, feature));
      }

      // reduce the providers to the smallest, unique collection taking into consideration the useProvider values
      reducedProviders = uniq(reducedProviders.map((providerName) => {
        const providerFeature = this.cloudProviderRegistry.getProvider(providerName)[feature] || {};
        return providerFeature.useProvider || providerName;
      }));

      if (reducedProviders.length > 1) {
        provider = this.$uibModal.open({
          templateUrl: require('./providerSelection.html'),
          controller: 'ProviderSelectCtrl as ctrl',
          resolve: {
            providerOptions: () =>  reducedProviders
          }
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
module (PROVIDER_SELECTION_SERVICE, [
  ACCOUNT_SERVICE,
  CLOUD_PROVIDER_REGISTRY,
]).service('providerSelectionService', ProviderSelectionService);

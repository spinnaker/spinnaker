import { module } from 'angular';

import { CLOUD_PROVIDER_LOGO } from './cloudProviderLogo.component';
import { SKIN_SELECTOR_CTRL } from './skinSelection/skinSelector.component';
import { CORE_CLOUDPROVIDER_CLOUDPROVIDERLABEL_DIRECTIVE } from './cloudProviderLabel.directive';
import { CORE_CLOUDPROVIDER_PROVIDERSELECTION_PROVIDERSELECTOR_DIRECTIVE } from './providerSelection/providerSelector.directive';

export const CLOUD_PROVIDER_MODULE = 'spinnaker.core.cloudProvider';
module(CLOUD_PROVIDER_MODULE, [
  CLOUD_PROVIDER_LOGO,
  CORE_CLOUDPROVIDER_CLOUDPROVIDERLABEL_DIRECTIVE,
  CORE_CLOUDPROVIDER_PROVIDERSELECTION_PROVIDERSELECTOR_DIRECTIVE,
  SKIN_SELECTOR_CTRL,
]);

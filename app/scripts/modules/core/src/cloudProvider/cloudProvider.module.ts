import { module } from 'angular';

import { CLOUD_PROVIDER_LOGO } from './cloudProviderLogo.component';
import { SKIN_SELECTOR_CTRL } from './skinSelection/skinSelector.component';

export const CLOUD_PROVIDER_MODULE = 'spinnaker.core.cloudProvider';
module(CLOUD_PROVIDER_MODULE, [
  CLOUD_PROVIDER_LOGO,
  require('./cloudProviderLabel.directive').name,
  require('./providerSelection/providerSelector.directive').name,
  SKIN_SELECTOR_CTRL,
]);

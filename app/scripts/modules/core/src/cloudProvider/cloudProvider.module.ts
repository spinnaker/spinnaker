import { module } from 'angular';

import { CLOUD_PROVIDER_LOGO } from './cloudProviderLogo.component';
import { VERSIONED_CLOUD_PROVIDER_SERVICE } from './versionedCloudProvider.service';
import { VERSION_SELECTOR_CTRL } from './versionSelection/versionSelector.component';

export const CLOUD_PROVIDER_MODULE = 'spinnaker.core.cloudProvider';
module(CLOUD_PROVIDER_MODULE, [
  CLOUD_PROVIDER_LOGO,
  require('./cloudProviderLabel.directive').name,
  require('./providerSelection/providerSelector.directive').name,
  VERSION_SELECTOR_CTRL,
  VERSIONED_CLOUD_PROVIDER_SERVICE,
]);

import { module } from 'angular';

import { CLOUD_PROVIDER_LOGO } from './cloudProviderLogo.component';
import { VERSIONED_CLOUD_PROVIDER_SERVICE } from './versionedCloudProvider.service';

export const CLOUD_PROVIDER_MODULE = 'spinnaker.core.cloudProvider';
module(CLOUD_PROVIDER_MODULE, [
  CLOUD_PROVIDER_LOGO,
  require('./cloudProviderLabel.directive').name,
  require('./providerSelection/providerSelector.directive').name,
  VERSIONED_CLOUD_PROVIDER_SERVICE,
]);

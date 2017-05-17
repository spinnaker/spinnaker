import { module } from 'angular';
import { react2angular } from 'react2angular';

import { CLOUD_PROVIDER_REGISTRY } from 'core/cloudProvider/cloudProvider.registry';
import { CloudProviderLogo } from './CloudProviderLogo';

export const CLOUD_PROVIDER_LOGO = 'spinnaker.core.cloudProviderLogo.directive';
module(CLOUD_PROVIDER_LOGO, [
  CLOUD_PROVIDER_REGISTRY,
])
  .component('cloudProviderLogo', react2angular(CloudProviderLogo, ['provider', 'height', 'width', 'showTooltip']));

import { module } from 'angular';

import { CloudProviderLogo } from './CloudProviderLogo';
import { angularComponentFromReact } from '../angular/angularComponentFromReact';

export const CLOUD_PROVIDER_LOGO = 'spinnaker.core.cloudProviderLogo.directive';
module(CLOUD_PROVIDER_LOGO, []).component(
  'cloudProviderLogo',
  angularComponentFromReact(CloudProviderLogo, 'cloudProviderLogo', ['provider', 'height', 'width', 'showTooltip']),
);

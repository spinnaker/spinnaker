import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';

import { CloudProviderLogo } from './CloudProviderLogo';

export const CLOUD_PROVIDER_LOGO = 'spinnaker.core.cloudProviderLogo.directive';
module(CLOUD_PROVIDER_LOGO, []).component(
  'cloudProviderLogo',
  react2angular(withErrorBoundary(CloudProviderLogo, 'cloudProviderLogo'), [
    'provider',
    'height',
    'width',
    'showTooltip',
  ]),
);

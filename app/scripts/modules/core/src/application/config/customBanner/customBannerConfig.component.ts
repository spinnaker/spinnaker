import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';
import { module } from 'angular';
import { react2angular } from 'react2angular';

import { CustomBannerConfig } from './CustomBannerConfig';

export const CUSTOM_BANNER_CONFIG = 'spinnaker.kubernetes.core.bannerConfig.component';
module(CUSTOM_BANNER_CONFIG, []).component(
  'customBannerConfig',
  react2angular(withErrorBoundary(CustomBannerConfig, 'customBannerConfig'), [
    'bannerConfigs',
    'isSaving',
    'saveError',
    'updateBannerConfigs',
  ]),
);

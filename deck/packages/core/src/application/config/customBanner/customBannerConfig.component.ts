import { module } from 'angular';

import { CustomBannerConfig } from './CustomBannerConfig';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';

export const CUSTOM_BANNER_CONFIG = 'spinnaker.kubernetes.core.bannerConfig.component';
module(CUSTOM_BANNER_CONFIG, []).component(
  'customBannerConfig',
  angularComponentFromReact(CustomBannerConfig, 'customBannerConfig', [
    'bannerConfigs',
    'isSaving',
    'saveError',
    'updateBannerConfigs',
  ]),
);

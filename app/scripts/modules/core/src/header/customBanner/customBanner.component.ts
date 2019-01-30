import { module } from 'angular';
import { react2angular } from 'react2angular';

import { CustomBanner } from './CustomBanner';

export const CUSTOM_BANNER = 'spinnaker.core.banner.component';
module(CUSTOM_BANNER, []).component('customBanner', react2angular(CustomBanner));

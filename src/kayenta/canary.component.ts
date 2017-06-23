import { module } from 'angular';
import { react2angular } from 'react2angular';

import { Canary } from './Canary';

export const CANARY_COMPONENT = 'spinnaker.kayenta.canary.component';
module(CANARY_COMPONENT, [])
  .component('canary', react2angular(Canary, ['application']));

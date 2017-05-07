import { module } from 'angular';
import { react2angular } from 'react2angular';

import { FastPropertyHistory } from './FastPropertyHistory';

export const FAST_PROPERTY_HISTORY_COMPONENT = 'spinnaker.netflix.fp.history.component';
module(FAST_PROPERTY_HISTORY_COMPONENT, [])
  .component('fastPropertyHistory', react2angular(FastPropertyHistory, ['property']));

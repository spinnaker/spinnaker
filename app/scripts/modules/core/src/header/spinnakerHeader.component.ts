import { module } from 'angular';
import { react2angular } from 'react2angular';

import { SpinnakerHeader } from './SpinnakerHeader';

export const SPINNAKER_HEADER = 'spinnaker.core.header.component';
module(SPINNAKER_HEADER, []).component('spinnakerHeader', react2angular(SpinnakerHeader));

import {module} from 'angular';
import 'ngimport';
import * as ReactGA from 'react-ga';

import {SETTINGS} from './modules/core/config/settings';

export const REACT_MODULE = 'spinnaker.react';

// Initialize React Google Analytics
if (SETTINGS.analytics.ga) {
  ReactGA.initialize(SETTINGS.analytics.ga, {});
}

module(REACT_MODULE, [
  'bcherny/ngimport',
]);

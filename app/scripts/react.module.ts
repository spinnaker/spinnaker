import {module} from 'angular';
import 'ngimport';
import * as ReactGA from 'react-ga';

import {SETTINGS} from './modules/core/config/settings';

// react component wrappers around angular components
import {ButtonBusyIndicatorInject} from './modules/core/forms/buttonBusyIndicator/ButtonBusyIndicator';

// Initialize React Google Analytics
if (SETTINGS.analytics.ga) {
  ReactGA.initialize(SETTINGS.analytics.ga, {});
}

export const REACT_MODULE = 'spinnaker.react';
module(REACT_MODULE, [
  'bcherny/ngimport',
]).run(function ($injector: any) {
  // Convert angular components to react
  ButtonBusyIndicatorInject($injector);
});

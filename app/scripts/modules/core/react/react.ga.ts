import * as ReactGA from 'react-ga';

import {SETTINGS} from 'core/config/settings';

if (SETTINGS.analytics.ga) {
  ReactGA.initialize(SETTINGS.analytics.ga, {});
}

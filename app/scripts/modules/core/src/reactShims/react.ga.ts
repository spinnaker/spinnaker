import ReactGA from 'react-ga';

import { SETTINGS } from 'core/config/settings';
import { logger } from 'core/utils';

export const initGoogleAnalytics = () => {
  if (!SETTINGS.analytics.ga) return;
  ReactGA.initialize(SETTINGS.analytics.ga, {}); // We're loading GA twice - here and in angular - but it shouldn't cause any problems
  logger.subscribe({
    key: 'googleAnalytics',
    onEvent: (event) => {
      ReactGA.event({ category: event.category, action: event.action, label: event.data?.label });
    },
  });
};

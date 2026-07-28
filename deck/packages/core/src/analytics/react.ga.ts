import ReactGA from 'react-ga';

import { SETTINGS } from '../config/settings';
import { logger } from '../utils';

export const initGoogleAnalytics = () => {
  if (!SETTINGS.analytics.ga) return;
  ReactGA.initialize(SETTINGS.analytics.ga, {});
  logger.subscribe({
    key: 'googleAnalytics',
    onEvent: (event) => {
      ReactGA.event({ category: event.category, action: event.action, label: event.data?.label });
    },
  });
};

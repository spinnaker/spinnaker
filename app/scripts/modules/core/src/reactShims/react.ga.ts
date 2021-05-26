import ReactGA from 'react-ga';

import { SETTINGS } from 'core/config/settings';
import { logger } from 'core/utils';

if (SETTINGS.analytics.ga) {
  ReactGA.initialize(SETTINGS.analytics.ga, {});
  logger.subscribe({
    key: 'googleAnalytics',
    onEvent: (event) => {
      ReactGA.event({ category: event.category, action: event.action, label: event.data?.label });
    },
  });
}

import { SETTINGS } from 'core/config/settings';
import ReactGA from 'react-ga';

if (SETTINGS.analytics.ga) {
  ReactGA.initialize(SETTINGS.analytics.ga, {});
}

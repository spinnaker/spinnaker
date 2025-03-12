import 'angulartics';
import 'angulartics-google-analytics';
import { SETTINGS } from '../config/settings';

if (SETTINGS.analytics.ga || SETTINGS.analytics.customConfig) {
  window.addEventListener('load', () => {
    if (SETTINGS.analytics.customConfig) {
      (window as any).ga('create', SETTINGS.analytics.ga, SETTINGS.analytics.customConfig);
    } else {
      (window as any).ga('create', SETTINGS.analytics.ga, 'auto');
    }
  });
}

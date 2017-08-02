import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ServerGroupAdvancedSettingsView } from './ServerGroupAdvancedSettingsView';

export const ADVANCED_SETTINGS_VIEW = 'spinnaker.amazon.serverGroup.advancedSettings.view';
module(ADVANCED_SETTINGS_VIEW, [])
  .component('awsAdvancedSettingsView', react2angular(ServerGroupAdvancedSettingsView, ['serverGroup']));

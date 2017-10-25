import { module } from 'angular';

import { CanarySettings } from 'kayenta/canary.settings';
import { CANARY_COMPONENTS } from 'kayenta/components/components.module';
import { CANARY_DATA_SOURCE } from 'kayenta/canary.dataSource';
import { CANARY_HELP } from 'kayenta/canary.help';
import { CANARY_STAGES } from 'kayenta/stages/stages.module';
import { CANARY_STATES } from 'kayenta/navigation/canary.states';
import 'kayenta/metricStore/index';
import 'kayenta/report/graph/chartjs';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function (key) {
  templates(key);
});

const modules = [
  CANARY_COMPONENTS,
  CANARY_DATA_SOURCE,
  CANARY_HELP,
  CANARY_STATES,
];

export const KAYENTA_MODULE = 'spinnaker.kayenta';
if (CanarySettings.featureDisabled) {
  module(KAYENTA_MODULE, []);
} else {
  module(
    KAYENTA_MODULE,
    CanarySettings.stagesEnabled
      ? [CANARY_STAGES, ...modules]
      : modules
  );
}

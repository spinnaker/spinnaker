import { module } from 'angular';
import { CANARY_DATA_SOURCE } from 'kayenta/canary.dataSource';
import 'kayenta/canary.help';
import { CanarySettings } from 'kayenta/canary.settings';
import { CANARY_COMPONENTS } from 'kayenta/components/components.module';
import 'kayenta/metricStore/index';
import { CANARY_STATES } from 'kayenta/navigation/canary.states';
import 'kayenta/report/detail/graph/semiotic';
import { CANARY_STAGES } from 'kayenta/stages/stages.module';

const modules = [CANARY_COMPONENTS, CANARY_DATA_SOURCE, CANARY_STATES];

export const KAYENTA_MODULE = 'spinnaker.kayenta';
if (CanarySettings.featureDisabled) {
  module(KAYENTA_MODULE, []);
} else if (CanarySettings.stagesEnabled === false) {
  module(KAYENTA_MODULE, modules);
} else {
  module(KAYENTA_MODULE, [CANARY_STAGES, ...modules]);
}

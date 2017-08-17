import { module } from 'angular';
import { react2angular } from 'react2angular';

import { CanaryScores } from './canaryScores';

export const CANARY_SCORES_CONFIG_COMPONENT = 'spinnaker.kayenta.canaryScores.component';
module(CANARY_SCORES_CONFIG_COMPONENT, [])
  .component('kayentaCanaryScores', react2angular(CanaryScores, ['onChange', 'successfulHelpFieldId', 'successfulLabel', 'successfulScore', 'unhealthyHelpFieldId', 'unhealthyLabel', 'unhealthyScore']));

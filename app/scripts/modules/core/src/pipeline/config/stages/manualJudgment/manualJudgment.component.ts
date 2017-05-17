import {module} from 'angular';
import {react2angular} from 'react2angular';

import {MANUAL_JUDGMENT_SERVICE} from './manualJudgment.service';
import {ManualJudgmentApproval} from './ManualJudgmentApproval';

export const MANUAL_JUDGMENT_COMPONENT = 'spinnaker.core.pipeline.config.stages.manualJudgment.component';
module(MANUAL_JUDGMENT_COMPONENT, [MANUAL_JUDGMENT_SERVICE])
  .component('manualJudgmentSelector', react2angular(ManualJudgmentApproval, ['execution', 'stage', 'application']));

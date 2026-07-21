import { module } from 'angular';

import { DiffSummary } from './DiffSummary';
import { angularComponentFromReact } from '../../../../angular/angularComponentFromReact';

export const DIFF_SUMMARY_COMPONENT = 'spinnaker.core.pipeline.config.diffSummary.component';
module(DIFF_SUMMARY_COMPONENT, []).component(
  'diffSummary',
  angularComponentFromReact(DiffSummary, 'diffSummary', ['summary']),
);

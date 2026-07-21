import { module } from 'angular';

import { ExecutionDetailsSectionNav } from './ExecutionDetailsSectionNav';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const EXECUTION_DETAILS_SECTION_NAV = 'spinnaker.core.pipeline.details.executionDetailsSectionNav';
module(EXECUTION_DETAILS_SECTION_NAV, []).component(
  'executionDetailsSectionNav',
  angularComponentFromReact(ExecutionDetailsSectionNav, 'executionDetailsSectionNav', ['sections']),
);

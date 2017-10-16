import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ExecutionDetailsSectionNav } from './ExecutionDetailsSectionNav';

export const EXECUTION_DETAILS_SECTION_NAV = 'spinnaker.core.delivery.details.executionDetailsSectionNav';
module(EXECUTION_DETAILS_SECTION_NAV, [])
  .component('executionDetailsSectionNav', react2angular(ExecutionDetailsSectionNav, ['sections']));

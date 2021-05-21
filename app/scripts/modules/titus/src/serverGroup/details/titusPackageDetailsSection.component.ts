import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { TitusPackageDetailsSection } from './TitusPackageDetailsSection';

export const TITUS_PACKAGE_DETAILS_SECTION = 'spinnaker.titus.serverGroup.details.packageDetails.component';
module(TITUS_PACKAGE_DETAILS_SECTION, []).component(
  'titusPackageDetailsSection',
  react2angular(withErrorBoundary(TitusPackageDetailsSection, 'titusPackageDetailsSection'), ['buildInfo']),
);

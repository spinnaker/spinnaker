import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from '@spinnaker/core';

import { TitusLaunchConfigSection } from './TitusLaunchConfigSection';

export const TITUS_SERVERGROUP_DETAILS_LAUNCHCONFIGSECTION = 'titus.servergroup.details.launchConfigSection';
module(TITUS_SERVERGROUP_DETAILS_LAUNCHCONFIGSECTION, []).component(
  'titusLaunchConfigSection',
  react2angular(withErrorBoundary(TitusLaunchConfigSection, 'titusLaunchConfigSection'), ['serverGroup']),
);

import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ProjectSummaryPod } from './ProjectSummaryPod';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const PROJECT_SUMMARY_POD_COMPONENT = 'spinnaker.core.search.infrastructure.projectSummaryPod.component';
module(PROJECT_SUMMARY_POD_COMPONENT, []).component(
  'projectSummaryPod',
  react2angular(withErrorBoundary(ProjectSummaryPod, 'projectSummaryPod'), ['projectName', 'applications']),
);

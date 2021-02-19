import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';

import { ProjectSummaryPod } from './ProjectSummaryPod';

export const PROJECT_SUMMARY_POD_COMPONENT = 'spinnaker.core.search.infrastructure.projectSummaryPod.component';
module(PROJECT_SUMMARY_POD_COMPONENT, []).component(
  'projectSummaryPod',
  react2angular(withErrorBoundary(ProjectSummaryPod, 'projectSummaryPod'), ['projectName', 'applications']),
);

import { module } from 'angular';

import { ProjectSummaryPod } from './ProjectSummaryPod';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const PROJECT_SUMMARY_POD_COMPONENT = 'spinnaker.core.search.infrastructure.projectSummaryPod.component';
module(PROJECT_SUMMARY_POD_COMPONENT, []).component(
  'projectSummaryPod',
  angularComponentFromReact(ProjectSummaryPod, 'projectSummaryPod', ['projectName', 'applications']),
);

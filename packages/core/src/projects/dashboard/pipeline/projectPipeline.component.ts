import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ProjectPipeline } from './ProjectPipeline';
import { withErrorBoundary } from '../../../presentation/SpinErrorBoundary';

export const PROJECT_PIPELINE_COMPONENT = 'spinnaker.core.projects.dashboard.pipelines.projectPipeline.component';
module(PROJECT_PIPELINE_COMPONENT, []).component(
  'projectPipeline',
  react2angular(withErrorBoundary(ProjectPipeline, 'projectPipeline'), ['execution', 'application']),
);

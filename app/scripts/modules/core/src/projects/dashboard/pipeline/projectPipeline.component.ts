import { module } from 'angular';
import { react2angular } from 'react2angular';

import { withErrorBoundary } from 'core/presentation/SpinErrorBoundary';

import { ProjectPipeline } from './ProjectPipeline';

export const PROJECT_PIPELINE_COMPONENT = 'spinnaker.core.projects.dashboard.pipelines.projectPipeline.component';
module(PROJECT_PIPELINE_COMPONENT, []).component(
  'projectPipeline',
  react2angular(withErrorBoundary(ProjectPipeline, 'projectPipeline'), ['execution', 'application']),
);

import { module } from 'angular';
import { react2angular } from 'react2angular';

import { ProjectPipeline } from './ProjectPipeline';

export const PROJECT_PIPELINE_COMPONENT = 'spinnaker.core.projects.dashboard.pipelines.projectPipeline.component';
module(PROJECT_PIPELINE_COMPONENT, []).component(
  'projectPipeline',
  react2angular(ProjectPipeline, ['execution', 'application']),
);

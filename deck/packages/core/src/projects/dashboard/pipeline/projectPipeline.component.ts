import { module } from 'angular';

import { ProjectPipeline } from './ProjectPipeline';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';

export const PROJECT_PIPELINE_COMPONENT = 'spinnaker.core.projects.dashboard.pipelines.projectPipeline.component';
module(PROJECT_PIPELINE_COMPONENT, []).component(
  'projectPipeline',
  angularComponentFromReact(ProjectPipeline, 'projectPipeline', ['execution', 'application']),
);

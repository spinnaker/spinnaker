import { module } from 'angular';
import { react2angular } from 'react2angular';

import { PipelineRoles } from './PipelineRoles';

export const PIPELINE_ROLES = 'spinnaker.core.pipeline.roles.component';
module(PIPELINE_ROLES, []).component('pipelineRoles', react2angular(PipelineRoles, ['roles', 'updateRoles']));

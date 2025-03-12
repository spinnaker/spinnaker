import { module } from 'angular';
import { react2angular } from 'react2angular';

import { CreatePipeline } from './CreatePipeline';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';

export const CREATE_PIPELINE_COMPONENT = 'spinnaker.core.pipeline.config.createNew.component';
module(CREATE_PIPELINE_COMPONENT, []).component(
  'createPipeline',
  react2angular(withErrorBoundary(CreatePipeline, 'createPipeline'), ['application']),
);

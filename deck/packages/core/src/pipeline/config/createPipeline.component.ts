import { module } from 'angular';

import { CreatePipeline } from './CreatePipeline';
import { angularComponentFromReact } from '../../angular/angularComponentFromReact';

export const CREATE_PIPELINE_COMPONENT = 'spinnaker.core.pipeline.config.createNew.component';
module(CREATE_PIPELINE_COMPONENT, []).component(
  'createPipeline',
  angularComponentFromReact(CreatePipeline, 'createPipeline', ['application']),
);

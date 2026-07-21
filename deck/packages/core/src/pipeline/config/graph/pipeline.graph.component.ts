import { module } from 'angular';

import { PipelineGraph } from './PipelineGraph';
import { angularComponentFromReact } from '../../../angular/angularComponentFromReact';

export const PIPELINE_GRAPH_COMPONENT = 'spinnaker.core.pipeline.config.graph.component';
module(PIPELINE_GRAPH_COMPONENT, []).component(
  'pipelineGraph',
  angularComponentFromReact(PipelineGraph, 'pipelineGraph', [
    'pipeline',
    'execution',
    'viewState',
    'onNodeClick',
    'shouldValidate',
  ]),
);

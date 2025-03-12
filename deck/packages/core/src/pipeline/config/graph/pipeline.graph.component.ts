import { module } from 'angular';
import { react2angular } from 'react2angular';

import { PipelineGraph } from './PipelineGraph';
import { withErrorBoundary } from '../../../presentation/SpinErrorBoundary';

export const PIPELINE_GRAPH_COMPONENT = 'spinnaker.core.pipeline.config.graph.component';
module(PIPELINE_GRAPH_COMPONENT, []).component(
  'pipelineGraph',
  react2angular(withErrorBoundary(PipelineGraph, 'pipelineGraph'), [
    'pipeline',
    'execution',
    'viewState',
    'onNodeClick',
    'shouldValidate',
  ]),
);

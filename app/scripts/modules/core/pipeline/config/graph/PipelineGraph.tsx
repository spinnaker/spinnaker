import * as React from 'react';
import { angular2react } from 'angular2react';

import { PipelineGraphComponent } from './pipeline.graph.component';
import { IExecution, IPipeline } from 'core/domain/index';
import { IExecutionViewState } from './pipelineGraph.service';

interface IPipelineGraphProps {
  pipeline?: IPipeline;
  execution: IExecution;
  viewState: IExecutionViewState;
  onNodeClick: (node: any) => void;
  shouldValidate?: boolean;
}

export let PipelineGraph: React.ComponentClass<IPipelineGraphProps> = undefined;
export const PipelineGraphInject = ($injector: any) => {
  PipelineGraph = angular2react<IPipelineGraphProps>('pipelineGraph', new PipelineGraphComponent(), $injector);
};

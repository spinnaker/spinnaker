import * as React from 'react';
import { angular2react } from 'angular2react';

import { IExecution, IPipeline } from 'core/domain/index';
import { IExecutionViewState } from './pipelineGraph.service';
import { IPipelineNode } from './pipelineGraph.service';
import { PipelineGraphComponent } from './pipeline.graph.component';

interface IPipelineGraphProps {
  pipeline?: IPipeline;
  execution: IExecution;
  viewState: IExecutionViewState;
  onNodeClick: (node: IPipelineNode) => void;
  shouldValidate?: boolean;
}

export let PipelineGraph: React.ComponentClass<IPipelineGraphProps> = undefined;
export const PipelineGraphInject = ($injector: any) => {
  PipelineGraph = angular2react<IPipelineGraphProps>('pipelineGraph', new PipelineGraphComponent(), $injector);
};

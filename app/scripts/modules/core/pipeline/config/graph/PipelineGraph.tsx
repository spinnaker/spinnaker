import * as React from 'react';
import { angular2react } from 'angular2react';

import { IExecution, IPipeline } from 'core/domain';
import { IExecutionViewState } from './pipelineGraph.service';
import { IPipelineNode } from './pipelineGraph.service';
import { PipelineGraphComponent } from './pipeline.graph.component';
import { ReactInjector } from 'core/react';

interface IPipelineGraphProps {
  pipeline?: IPipeline;
  execution: IExecution;
  viewState: IExecutionViewState;
  onNodeClick: (node: IPipelineNode) => void;
  shouldValidate?: boolean;
}

export let PipelineGraph: React.ComponentClass<IPipelineGraphProps> = undefined;
ReactInjector.give(($injector: any) => PipelineGraph = angular2react('pipelineGraph', new PipelineGraphComponent(), $injector) as any);

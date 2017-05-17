import { IExecution, IPipeline } from 'core/domain';
import { IExecutionViewState } from './pipelineGraph.service';
import { IPipelineNode } from './pipelineGraph.service';

export interface IPipelineGraphProps {
  pipeline?: IPipeline;
  execution: IExecution;
  viewState: IExecutionViewState;
  onNodeClick: (node: IPipelineNode) => void;
  shouldValidate?: boolean;
}

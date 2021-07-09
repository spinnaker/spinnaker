import { IExecution, IExecutionStageSummary, IPipeline, IStage } from '../../../domain';

export interface IExecutionViewState {
  activeStageId: number;
  activeSubStageId: number;
  canConfigure: boolean;
  canTriggerPipelineManually: boolean;
  executionId?: string;
  section?: string;
  stageIndex?: number;
}

export interface IPipelineGraphLink {
  child: IPipelineGraphNode;
  isHighlighted?: boolean;
  line: string;
  linkClass?: string; // Added after the fact in PipelineGraphDirective
  parent: IPipelineGraphNode;
}

export interface IPipelineGraphNode {
  childLinks: IPipelineGraphLink[];
  children: IPipelineGraphNode[];
  color?: string;
  height?: number; // Added after the fact in PipelineGraphDirective
  id: string | number;
  index?: number;
  leaf?: boolean;
  name: string;
  parentIds: Array<string | number>;
  parentLinks: IPipelineGraphLink[];
  parents: IPipelineGraphNode[];
  placeholder?: boolean;
  root?: boolean;
  graphRowOverride?: number;
  row?: number; // Added after the fact in PipelineGraphDirective
  x?: number; // Added after the fact in PipelineGraphDirective
  y?: number; // Added after the fact in PipelineGraphDirective

  // PipelineGraphComponent conflates the two node types, so adding as optional here
  // Config node parameters
  extraLabelLines?: number;
  hasWarnings?: boolean;
  isActive: boolean;
  isHighlighted: boolean;
  lastPhase?: number; // Added after the fact in PipelineGraphDirective
  phase?: number;
  section?: string;
  warnings?: { messages: string[] };

  // Execution node parameters
  executionStage?: boolean;
  hasNotStarted?: boolean;
  labelComponent?: React.ComponentType<{ stage: IExecutionStageSummary }>;
  masterStage?: IStage;
  stage?: IExecutionStageSummary;
  status?: string;
}

export class PipelineGraphService {
  public static xScrollOffset: any = {};

  public static generateExecutionGraph(execution: IExecution, viewState: IExecutionViewState) {
    const nodes: IPipelineGraphNode[] = [];
    (execution.stageSummaries || []).forEach((stage: IExecutionStageSummary, idx: number) => {
      const parentIds = (stage.requisiteStageRefIds || []).slice();
      const node: IPipelineGraphNode = {
        childLinks: [],
        children: [],
        executionStage: true,
        extraLabelLines: stage.extraLabelLines ? stage.extraLabelLines(stage) : 0,
        graphRowOverride: stage.graphRowOverride || 0,
        hasNotStarted: stage.hasNotStarted,
        id: stage.refId,
        index: idx,
        isActive: viewState.activeStageId === stage.index,
        isHighlighted: false,
        labelComponent: stage.labelComponent,
        masterStage: stage.masterStage,
        name: stage.name,
        parentIds,
        parentLinks: [],
        parents: [],
        stage,
        status: stage.status,
      };
      if (!node.parentIds.length) {
        node.root = true;
      }
      nodes.push(node);
    });

    return nodes;
  }

  public static generateConfigGraph(pipeline: IPipeline, viewState: IExecutionViewState, pipelineValidations: any) {
    const nodes: IPipelineGraphNode[] = [];
    const configWarnings = pipelineValidations.pipeline;
    const configNode: IPipelineGraphNode = {
      childLinks: [],
      children: [],
      hasWarnings: !!configWarnings.length,
      id: -1,
      isActive: viewState.section === 'triggers',
      isHighlighted: false,
      name: 'Configuration',
      parentIds: [],
      parentLinks: [],
      parents: [],
      phase: 0,
      root: true,
      section: 'triggers',
      warnings: configWarnings.length ? { messages: configWarnings } : null,
    };
    nodes.push(configNode);

    pipeline.stages.forEach(function (stage: IExecutionStageSummary, idx: number) {
      const warnings = pipelineValidations.stages.find((e: any) => e.stage === stage);
      const parentIds = (stage.requisiteStageRefIds || []).slice();
      const node: IPipelineGraphNode = {
        childLinks: [],
        children: [],
        graphRowOverride: stage.graphRowOverride || 0,
        hasWarnings: !!warnings,
        id: stage.refId,
        index: idx,
        isActive: viewState.stageIndex === idx && viewState.section === 'stage',
        isHighlighted: false,
        name: stage.name || '[new stage]',
        parentIds,
        parentLinks: [],
        parents: [],
        root: false,
        section: 'stage',
        warnings,
      };
      if (!node.parentIds.length) {
        node.parentIds.push(configNode.id);
      }
      nodes.push(node);
    });

    return nodes;
  }
}

import {module} from 'angular';
import {IExecution, IPipeline, IStage, IStageSummary} from 'core/domain/index';

export interface IExecutionViewState {
  activeStageId: number;
  executionId: string;
  canTriggerPipelineManually: boolean;
  canConfigure: boolean;
  showStageDuration: boolean;
  section?: string;
  stageIndex?: number;
};

export interface IPipelineLink {
  parent: IPipelineNode;
  child: IPipelineNode;
  line: string;
  isHighlighted?: boolean;
  linkClass?: string; // Added after the fact in PipelineGraphDirective
};

export interface IPipelineNode {
  id: (string | number);
  index?: number;
  name: string;
  parentIds: (string | number)[];
  parents: IPipelineNode[];
  children: IPipelineNode[];
  parentLinks: IPipelineLink[];
  childLinks: IPipelineLink[];
  isActive: boolean;
  isHighlighted: boolean;
  root?: boolean;
  leaf?: boolean;
  height?: number; // Added after the fact in PipelineGraphDirective
  row?: number; // Added after the fact in PipelineGraphDirective
  x?: number; // Added after the fact in PipelineGraphDirective
  y?: number; // Added after the fact in PipelineGraphDirective

  // PipelineGraphDirective conflates the two node types, so adding as optional here
  // Config node parameters
  phase?: number;
  lastPhase?: number; // Added after the fact in PipelineGraphDirective
  extraLabelLines?: number;
  section?: string;
  warnings?: { messages: string[] };
  hasWarnings?: boolean;

  // Execution node parameters
  stage?: IStageSummary;
  masterStage?: IStage;
  executionStage?: boolean;
  hasNotStarted?: boolean;
  status?: string;
  labelTemplateUrl?: string;
  executionId?: string;
}

export class PipelineGraphService {
  public xScrollOffset: any = {};

  public generateExecutionGraph(execution: IExecution, viewState: IExecutionViewState) {
    const nodes: IPipelineNode[] = [];
    (execution.stageSummaries || []).forEach(function(stage: IStageSummary, idx: number) {
      const node: IPipelineNode = {
        id: stage.refId,
        name: stage.name,
        index: idx,
        parentIds: Object.assign([], (stage.requisiteStageRefIds || [])),
        stage: stage,
        masterStage: stage.masterStage,
        labelTemplateUrl: stage.labelTemplateUrl,
        extraLabelLines: stage.extraLabelLines ? stage.extraLabelLines(stage) : 0,
        parents: [],
        children: [],
        parentLinks: [],
        childLinks: [],
        isActive: viewState.activeStageId === stage.index && viewState.executionId === execution.id,
        isHighlighted: false,
        status: stage.status,
        executionStage: true,
        hasNotStarted: stage.hasNotStarted,
        executionId: execution.id,
      };
      if (!node.parentIds.length) {
        node.root = true;
      }
      nodes.push(node);
    });

    return nodes;
  }

  public generateConfigGraph(pipeline: IPipeline, viewState: IExecutionViewState, pipelineValidations: any) {
    const nodes: IPipelineNode[] = [];
    const configWarnings = pipelineValidations.pipeline;
    const configNode: IPipelineNode = {
          name: 'Configuration',
          phase: 0,
          id: -1,
          section: 'triggers',
          parentIds: [],
          parents: [],
          children: [],
          parentLinks: [],
          childLinks: [],
          root: true,
          isActive: viewState.section === 'triggers',
          isHighlighted: false,
          warnings: configWarnings.length ? {messages: configWarnings} : null,
          hasWarnings: !!configWarnings.length,
        };
    nodes.push(configNode);

    pipeline.stages.forEach(function(stage: IStageSummary, idx: number) {
      const warnings = pipelineValidations.stages.find((e: any) => e.stage === stage);
      const node: IPipelineNode = {
        id: stage.refId,
        name: stage.name || '[new stage]',
        section: 'stage',
        index: idx,
        parentIds: Object.assign([], (stage.requisiteStageRefIds || [])),
        parents: [],
        children: [],
        parentLinks: [],
        childLinks: [],
        isActive: viewState.stageIndex === idx && viewState.section === 'stage',
        isHighlighted: false,
        warnings: warnings,
        hasWarnings: !!warnings,
        root: false
      };
      if (!node.parentIds.length) {
        node.parentIds.push(configNode.id);
      }
      nodes.push(node);
    });

    return nodes;
  }
}

export const PIPELINE_GRAPH_SERVICE = 'spinnaker.core.pipeline.config.graph.pipelineGraph.service';
module(PIPELINE_GRAPH_SERVICE, [])
  .service('pipelineGraphService', PipelineGraphService);

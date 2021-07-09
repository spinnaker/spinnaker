import classNames from 'classnames';
import DOMPurify from 'dompurify';
import $ from 'jquery';
import { clone, find, flatten, forOwn, groupBy, max, maxBy, sortBy, sum, sumBy, uniq } from 'lodash';
import { Debounce, Throttle } from 'lodash-decorators';
import React from 'react';
import { Subscription } from 'rxjs';

import { PipelineGraphLink } from './PipelineGraphLink';
import { PipelineGraphNode } from './PipelineGraphNode';
import { IExecution, IPipeline } from '../../../domain';
import {
  IExecutionViewState,
  IPipelineGraphLink,
  IPipelineGraphNode,
  PipelineGraphService,
} from './pipelineGraph.service';
import { UUIDGenerator } from '../../../utils/uuid.service';
import { IPipelineValidationResults } from '../validation/PipelineConfigValidator';
import { PipelineConfigValidator } from '../validation/PipelineConfigValidator';

import './pipelineGraph.less';

export interface IPipelineGraphProps {
  execution?: IExecution;
  onNodeClick: (node: IPipelineGraphNode, subIndex?: number) => void;
  pipeline?: IPipeline;
  shouldValidate?: boolean;
  viewState: IExecutionViewState;
}

export interface IPipelineGraphState {
  allNodes: IPipelineGraphNode[];
  graphHeight: number;
  graphWidth: string;
  labelOffsetX: number;
  labelOffsetY: number;
  maxLabelWidth: number;
  nodeRadius: number;
  phaseCount: number;
  rowHeights: number[];
}

export class PipelineGraph extends React.Component<IPipelineGraphProps, IPipelineGraphState> {
  private defaultNodeRadius = 8;
  private defaultState: IPipelineGraphState = {
    allNodes: [],
    graphHeight: 0,
    graphWidth: '',
    labelOffsetX: this.defaultNodeRadius + 3,
    labelOffsetY: this.defaultNodeRadius + 10,
    maxLabelWidth: 0,
    nodeRadius: this.defaultNodeRadius,
    phaseCount: 0,
    rowHeights: [],
  };
  private element: JQuery;
  private graphStatusHash: string;
  private graphVerticalPadding = 11;
  private minExecutionGraphHeight = 40;
  private minLabelWidth = 100;
  private pipelineValidations: IPipelineValidationResults = { pipeline: [], stages: [] };
  private rowPadding = 20;
  private validationSubscription: Subscription;
  private windowResize = this.handleWindowResize.bind(this);

  constructor(props: IPipelineGraphProps) {
    super(props);
    const { execution } = props;
    this.state = this.defaultState;

    // HACK: This is needed to update the node states in the graph based on the stage states.
    //       Once the execution itself changes based on stage status, this can be removed.
    if (execution) {
      this.graphStatusHash = execution.graphStatusHash;
    }
  }

  private highlight = (node: IPipelineGraphNode, highlight: boolean): void => {
    if (node.isActive) {
      return;
    }
    node.isHighlighted = highlight;
    node.parentLinks.forEach((link: IPipelineGraphLink) => (link.isHighlighted = highlight));
    node.childLinks.forEach((link: IPipelineGraphLink) => (link.isHighlighted = highlight));
    this.applyAllNodes(this.state);
  };

  @Throttle(300)
  private handleWindowResize(): void {
    this.updateGraph(this.props);
  }

  private handleWheel = (e: React.WheelEvent<HTMLDivElement>) => {
    // track and save the graph scroll position for executions so it doesn't get reset to
    // zero every second due to repaint.
    if (this.props.execution) {
      PipelineGraphService.xScrollOffset[this.props.execution.id] = (e.target as HTMLElement).parentElement.scrollLeft;
    }
  };

  /**
   * Used to draw inverse bezier curve between stages
   */
  private curvedLink(d: any) {
    const sourceX = d.source.x + this.state.nodeRadius;
    const targetX = d.target.x - this.state.nodeRadius;
    const curve = (sourceX + targetX) / 2;
    return `M${sourceX},${d.source.y}C${curve},${d.source.y} ${curve},${d.target.y} ${targetX},${d.target.y}`;
  }

  private getLastPhase(node: IPipelineGraphNode): number {
    if (!node.children.length) {
      return node.phase;
    }
    return max(node.children.map((n) => this.getLastPhase(n)));
  }

  private createNodes(props: IPipelineGraphProps): IPipelineGraphNode[] {
    const { execution, pipeline, viewState } = props;
    return pipeline
      ? PipelineGraphService.generateConfigGraph(pipeline, viewState, this.pipelineValidations)
      : PipelineGraphService.generateExecutionGraph(execution, viewState);
  }

  /**
   * Sets phases and adds children/parents to nodes
   * Probably blows the stack if circular dependencies exist, maybe not
   */
  private applyPhasesAndLink(
    props: IPipelineGraphProps,
    nodes: IPipelineGraphNode[],
    newState: IPipelineGraphState,
  ): IPipelineGraphNode[][] {
    nodes = nodes || this.createNodes(props);
    let allPhasesResolved = true;
    nodes.forEach((node) => {
      let phaseResolvable = true;
      let phase = 0;
      if (!node.parentIds.length) {
        node.phase = phase;
      } else {
        node.parentIds.forEach((parentId) => {
          const parent = find(nodes, { id: parentId });
          if (parent && parent.phase !== undefined) {
            phase = Math.max(phase, parent.phase);
            parent.children.push(node);
            node.parents.push(parent);
          } else {
            phaseResolvable = false;
          }
        });
        if (phaseResolvable) {
          node.phase = phase + 1;
        } else {
          allPhasesResolved = false;
        }
      }
    });
    if (!allPhasesResolved) {
      return this.applyPhasesAndLink(props, nodes, newState);
    } else {
      const highestPhaseNode = maxBy(nodes, 'phase');
      newState.phaseCount = highestPhaseNode ? highestPhaseNode.phase : 0;
      if (newState.phaseCount > 6) {
        newState.nodeRadius = 6;
        newState.labelOffsetX = newState.nodeRadius + 3;
        newState.labelOffsetY = 15;
      }
      const groupedNodes: IPipelineGraphNode[][] = [];
      nodes.forEach((node: IPipelineGraphNode) => {
        node.children = uniq(node.children);
        node.parents = uniq(node.parents);
        node.leaf = node.children.length === 0;
      });
      nodes.forEach((node) => (node.lastPhase = this.getLastPhase(node)));

      // Collision minimization "Algorithm"
      const grouped = groupBy(nodes, 'phase');
      forOwn(grouped, (group: IPipelineGraphNode[], phase: any) => {
        const sortedPhase = sortBy(
          group,
          // farthest, highest parent, e.g. phase 1 always before phase 2, row 1 always before row 2
          (node: IPipelineGraphNode) => {
            if (node.parents.length) {
              const parents = sortBy(
                node.parents,
                (parent: IPipelineGraphNode) => 1 - parent.phase,
                (parent: IPipelineGraphNode) => parent.row,
              );
              const firstParent = parents[0];
              return firstParent.phase * 100 + firstParent.row;
            }
            return 0;
          },
          (node: IPipelineGraphNode) => (node.graphRowOverride ? node.graphRowOverride : 1000),
          // same highest parent, prefer farthest last node
          (node: IPipelineGraphNode) => 1 - node.lastPhase,
          // same highest parent, prefer fewer terminal children if any
          (node: IPipelineGraphNode) => node.children.filter((child) => !child.children.length).length || 100,
          // same highest parent, same number of terminal children, prefer fewer parents
          (node: IPipelineGraphNode) => node.parents.length,
          // same highest parent, same number of terminal children and parents
          (node: IPipelineGraphNode) => 1 - node.children.length,
          // same number of children, so sort by number of grandchildren (more first)
          (node: IPipelineGraphNode) => 1 - sumBy(node.children, (child: IPipelineGraphNode) => child.children.length),
          // great, same number of grandchildren, how about by nearest children, alphabetically by name, why not
          (node: IPipelineGraphNode) =>
            sortBy(node.children, 'phase')
              .map((child: IPipelineGraphNode) => [child.phase - node.phase, child.name].join('-'))
              .join(':'),
          // if `id` is a number (or a string that maps to a number), sort above ids that are strings.
          (node: IPipelineGraphNode) => (Number.isNaN(Number(node.id)) ? Number.MAX_SAFE_INTEGER : Number(node.id)),
          // if `id` is a string.
          ['id'],
        );
        sortedPhase.forEach((node: IPipelineGraphNode, index: number) => {
          node.row = index;
        });
        groupedNodes[phase] = sortedPhase;
      });
      this.fixOverlaps(groupedNodes);
      return groupedNodes;
    }
  }

  // if any nodes in the same row as a parent node, but not in the immediately preceding phase, inject placeholder nodes
  // so there are no overlapping links
  private fixOverlaps(nodes: IPipelineGraphNode[][]): void {
    nodes.forEach((column) => {
      column.forEach((node) => {
        const nonImmediateChildren = node.children.filter((c) => c.phase - node.phase > 1 && c.row === node.row);
        nonImmediateChildren.forEach((child) => {
          for (let phase = node.phase + 1; phase < child.phase; phase++) {
            if (nodes[phase].length >= node.row) {
              nodes[phase].splice(node.row, 0, this.createPlaceholderNode(node.row, phase));
              nodes[phase].forEach((n: IPipelineGraphNode, index: number) => {
                n.row = index;
              });
            }
          }
        });
      });
    });
  }

  private createPlaceholderNode(row: number, phase: number): IPipelineGraphNode {
    return {
      childLinks: [],
      children: [],
      id: UUIDGenerator.generateUuid(),
      isActive: false,
      isHighlighted: false,
      name: '',
      parentIds: [],
      parentLinks: [],
      parents: [],
      phase,
      placeholder: true,
      row,
    };
  }

  /**
   * Sets the width of the graph and determines the width available for each label
   */
  private applyPhaseWidth(props: IPipelineGraphProps, newState: IPipelineGraphState): void {
    const phaseOffset = 2 * newState.nodeRadius + newState.labelOffsetX;
    newState.maxLabelWidth = this.element.width() - 2 * newState.nodeRadius;

    if (newState.phaseCount) {
      newState.maxLabelWidth = newState.maxLabelWidth / (newState.phaseCount + 1) - phaseOffset;
    }
    newState.maxLabelWidth = Math.max(this.minLabelWidth, newState.maxLabelWidth);

    if (newState.maxLabelWidth === this.minLabelWidth) {
      newState.graphWidth = (newState.phaseCount + 1) * (newState.maxLabelWidth + phaseOffset) + 5 + 'px';
    } else {
      newState.graphWidth = '100%';
    }

    // get the saved horizontal scroll position for executions
    if (props.execution) {
      const offsetForId = PipelineGraphService.xScrollOffset[props.execution.id] || 0;
      this.element.scrollLeft(offsetForId);
    }
  }

  private applyNodeHeights(groupedNodes: IPipelineGraphNode[][], newState: IPipelineGraphState): void {
    const placeholderNode = this.element.find('g.placeholder div');
    placeholderNode.width(newState.maxLabelWidth);
    let graphHeight = 0;
    groupedNodes.forEach((nodes: IPipelineGraphNode[]) => {
      nodes.forEach((node) => {
        const extraLines = node.extraLabelLines ? '<div>x</div>'.repeat(node.extraLabelLines) : '';
        placeholderNode.html(`<a href>${DOMPurify.sanitize(node.name)}${extraLines}</a>`);
        node.height = placeholderNode.height() + this.rowPadding;
      });
      graphHeight = Math.max(sumBy(nodes, 'height'), graphHeight);
    });
    placeholderNode.empty();
    newState.graphHeight += 3 * this.graphVerticalPadding;
  }

  private setNodePositions(groupedNodes: IPipelineGraphNode[][], newState: IPipelineGraphState): void {
    groupedNodes.forEach((nodes: IPipelineGraphNode[], idx: number) => {
      let nodeOffset = this.graphVerticalPadding;
      nodes.forEach((node, rowNumber) => {
        node.x = (newState.maxLabelWidth + 2 * newState.nodeRadius + newState.labelOffsetX) * idx;
        node.y = nodeOffset;
        nodeOffset += newState.rowHeights[rowNumber];
      });
    });
  }

  private createLinks(newState: IPipelineGraphState): void {
    newState.allNodes.forEach((node) => {
      node.children.forEach((child) => {
        this.linkNodes(child, node);
      });
    });
  }

  private linkNodes(child: IPipelineGraphNode, parent: IPipelineGraphNode): void {
    const link: IPipelineGraphLink = {
      parent,
      child,
      line: this.curvedLink({ source: parent, target: child }),
    };
    parent.childLinks.push(link);
    child.parentLinks.push(link);
  }

  private applyAllNodes(newState: IPipelineGraphState): void {
    const highlightedNodeIndex = newState.allNodes.findIndex((node) => node.isHighlighted);
    const activeNodeIndex = newState.allNodes.findIndex((node) => node.isActive);
    if (activeNodeIndex !== -1) {
      const node = newState.allNodes.splice(highlightedNodeIndex, 1)[0];
      newState.allNodes.push(node);
    }
    if (highlightedNodeIndex !== -1) {
      const node = newState.allNodes.splice(highlightedNodeIndex, 1)[0];
      newState.allNodes.push(node);
    }
    this.setState(newState);
  }

  private establishRowHeights(groupedNodes: IPipelineGraphNode[][], newState: IPipelineGraphState): void {
    const rowHeights: number[] = [];
    groupedNodes.forEach((column: IPipelineGraphNode[]) => {
      column.forEach((node, rowNumber) => {
        if (!rowHeights[rowNumber]) {
          rowHeights[rowNumber] = 0;
        }
        rowHeights[rowNumber] = Math.max(rowHeights[rowNumber], node.height);
      });
    });
    newState.rowHeights = rowHeights;
    newState.graphHeight = Math.max(sum(newState.rowHeights) + this.graphVerticalPadding, this.minExecutionGraphHeight);
  }

  private updateGraph(props: IPipelineGraphProps, statesOnly = false): void {
    let newState: IPipelineGraphState;
    if (!statesOnly) {
      newState = clone(this.defaultState);
      const groupedNodes = this.applyPhasesAndLink(props, null, newState);
      this.applyPhaseWidth(props, newState);
      this.applyNodeHeights(groupedNodes, newState);
      this.establishRowHeights(groupedNodes, newState);
      this.setNodePositions(groupedNodes, newState);
      newState.allNodes = flatten(groupedNodes);
      this.createLinks(newState);
    } else {
      newState = { allNodes: this.state.allNodes } as IPipelineGraphState;
      newState.allNodes.forEach((node) => this.resetLinks(props, node));
    }
    this.applyAllNodes(newState);
  }

  private resetLinks(props: IPipelineGraphProps, node: IPipelineGraphNode): void {
    const { activeStageId, section, stageIndex } = props.viewState;
    if (props.execution) {
      // executions view
      node.isActive = activeStageId === node.index;
    } else {
      // pipeline config view
      if (node.section === 'triggers') {
        node.isActive = section === node.section;
      } else {
        node.isActive = stageIndex === node.index && section === 'stage';
      }
    }
    node.isHighlighted = false;
    node.parentLinks.forEach((link) => (link.isHighlighted = false));
    node.childLinks.forEach((link) => (link.isHighlighted = false));
  }

  public componentDidMount() {
    window.addEventListener('resize', this.windowResize);
    this.validationSubscription = PipelineConfigValidator.subscribe((validations) => {
      this.pipelineValidations = validations;
      this.updateGraph(this.props);
    });
    this.updateGraph(this.props);
  }

  private refCallback = (element: HTMLDivElement): void => {
    if (element) {
      this.element = $(element);
    }
  };

  @Debounce(300)
  private validatePipeline(pipeline: IPipeline): void {
    PipelineConfigValidator.validatePipeline(pipeline).catch(() => {});
  }

  public componentWillReceiveProps(nextProps: IPipelineGraphProps) {
    let updateGraph = false;
    let stateOnly = true;

    if (
      (nextProps.execution && this.graphStatusHash !== nextProps.execution.graphStatusHash) ||
      nextProps.execution !== this.props.execution
    ) {
      this.graphStatusHash = nextProps.execution.graphStatusHash;
      updateGraph = true;
      stateOnly = false;
    }

    if (nextProps.pipeline !== this.props.pipeline) {
      updateGraph = true;
      stateOnly = false;
      if (this.props.shouldValidate) {
        this.validatePipeline(nextProps.pipeline);
      }
    }

    if (nextProps.viewState !== this.props.viewState) {
      updateGraph = true;
    }

    if (updateGraph) {
      this.updateGraph(nextProps, stateOnly);
    }
  }

  public componentWillUnmount() {
    this.validationSubscription.unsubscribe();
    window.removeEventListener('resize', this.windowResize);
  }

  public render() {
    const { execution } = this.props;
    const { allNodes, graphHeight, graphWidth, labelOffsetX, labelOffsetY, maxLabelWidth, nodeRadius } = this.state;

    return (
      <div className="pipeline-graph" ref={this.refCallback} onWheel={this.handleWheel}>
        <svg
          className="pipeline-graph"
          style={{
            height: graphHeight,
            width: graphWidth,
            padding: this.graphVerticalPadding + 'px ' + nodeRadius * 2 + 'px ' + '0 ' + nodeRadius * 2 + 'px',
          }}
        >
          <g className="placeholder">
            <foreignObject width={maxLabelWidth > 0 ? maxLabelWidth : 1} height="200">
              <div className="label-body node active" />
            </foreignObject>
          </g>
          {allNodes.map(
            (node) =>
              !node.placeholder && (
                <g
                  key={node.id}
                  className={classNames({
                    'has-status': !!node.status,
                    active: node.isActive,
                    highlighted: node.isHighlighted,
                    warning: node.hasWarnings,
                  })}
                  transform={`translate(${node.x},${node.y})`}
                >
                  {node.childLinks.map((link) => (
                    <PipelineGraphLink key={`${link.child.id}_${link.parent.name}`} link={link} x={node.x} y={node.y} />
                  ))}
                  <PipelineGraphNode
                    isExecution={!!execution}
                    labelOffsetX={labelOffsetX}
                    labelOffsetY={labelOffsetY}
                    maxLabelWidth={maxLabelWidth}
                    nodeClicked={this.props.onNodeClick}
                    highlight={this.highlight}
                    nodeRadius={nodeRadius}
                    node={node}
                  />
                </g>
              ),
          )}
        </svg>
      </div>
    );
  }
}

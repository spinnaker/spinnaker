import { debounce, filter, find, flatten, forOwn, groupBy, max, maxBy, sortBy, sum, sumBy, throttle, uniq } from 'lodash';
import {ISCEService, module} from 'angular';

import { IExecution, IPipeline } from 'core/domain/index';
import { IPipelineValidationResults, PIPELINE_CONFIG_VALIDATOR, PipelineConfigValidator } from '../validation/pipelineConfig.validator';
import { IExecutionViewState, IPipelineLink, IPipelineNode, PIPELINE_GRAPH_SERVICE, PipelineGraphService } from './pipelineGraph.service';
import { UUIDGenerator } from 'core/utils/uuid.service';
import { LABEL_COMPONENT } from 'core/presentation/label.component';

require('./pipelineGraph.less');

export class PipelineGraphController implements ng.IComponentController {
  public pipeline: IPipeline;
  public execution: IExecution;
  public viewState: IExecutionViewState;
  public onNodeClick: (node: any) => void;
  public shouldValidate: boolean;
  private pipelineValidations: IPipelineValidationResults = { pipeline: [], stages: [] };
  private minLabelWidth = 100;

  static get $inject() {
    return ['$sce', '$scope', '$element', '$', '$window', 'pipelineGraphService', 'pipelineConfigValidator'];
  }

  public constructor(private $sce: ISCEService,
                     private $scope: any,
                     private $element: JQuery,
                     private $: JQueryStatic,
                     private $window: ng.IWindowService,
                     private pipelineGraphService: PipelineGraphService,
                     private pipelineConfigValidator: PipelineConfigValidator) {}

  /**
   * Used to draw inverse bezier curve between stages
   */
  private curvedLink(d: any) {
    const sourceX = d.source.x + this.$scope.nodeRadius;
    const targetX = d.target.x - this.$scope.nodeRadius;
    return 'M' + sourceX + ',' + d.source.y
        + 'C' + (sourceX + targetX) / 2 + ',' + d.source.y
        + ' ' + (sourceX + targetX) / 2 + ',' + d.target.y
        + ' ' + targetX + ',' + d.target.y;
  }

  private setLinkClass(link: IPipelineLink): void {
    const child = link.child,
        parent = link.parent;
    const linkClasses: string[] = [];
    if (link.isHighlighted) {
      linkClasses.push('highlighted');
    }
    if (child.isActive || parent.isActive) {
      linkClasses.push('active');
      if (!child.executionStage) {
        linkClasses.push(child.isActive ? 'target' : 'source');
      }
    }
    if (child.executionStage) {
      if (child.hasNotStarted) {
        linkClasses.push(child.status.toLowerCase());
      } else {
        linkClasses.push(parent.status.toLowerCase());
      }
      linkClasses.push('has-status');
    }
    link.linkClass = linkClasses.join(' ');
  };

  private getLastPhase(node: IPipelineNode): number {
    if (!node.children.length) {
      return node.phase;
    }
    return max(node.children.map((n) => this.getLastPhase(n)));
  }

  private createNodes(): IPipelineNode[] {
    return this.pipeline ?
    this.pipelineGraphService.generateConfigGraph(this.pipeline, this.viewState, this.pipelineValidations) :
    this.pipelineGraphService.generateExecutionGraph(this.execution, this.viewState);
  }

  /**
   * Sets phases and adds children/parents to nodes
   * Probably blows the stack if circular dependencies exist, maybe not
   */
  private applyPhasesAndLink(nodes: (IPipelineNode)[]): void {
    nodes = nodes || this.createNodes();
    let allPhasesResolved = true;
    nodes.forEach((node) => {
      let phaseResolvable = true,
          phase = 0;
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
      this.applyPhasesAndLink(nodes);
    } else {
      this.$scope.phaseCount = maxBy(nodes, 'phase').phase;
      if (this.$scope.phaseCount > 6) {
        this.$scope.nodeRadius = 6;
        this.$scope.labelOffsetX = this.$scope.nodeRadius + 3;
        this.$scope.labelOffsetY = 15;
      }
      this.$scope.nodes = [];
      nodes.forEach((node: IPipelineNode) => {
        node.children = uniq(node.children);
        node.parents = uniq(node.parents);
        node.leaf = node.children.length === 0;
      });
      nodes.forEach((node) => {
        node.lastPhase = this.getLastPhase(node);
      });

      // Collision minimization "Algorithm"
      const grouped = groupBy(nodes, 'phase');
      forOwn(grouped, (group: IPipelineNode[], phase: string) => {
        const sortedPhase = sortBy(group,
          // farthest, highest parent, e.g. phase 1 always before phase 2, row 1 always before row 2
          (node: IPipelineNode) => {
            if (node.parents.length) {
              const parents = sortBy(node.parents,
                                      (parent: IPipelineNode) => 1 - parent.phase,
                                      (parent: IPipelineNode) => parent.row
                                    );

              const firstParent = parents[0];
              return (firstParent.phase * 100) + firstParent.row;
            }
            return 0;
          },
          // same highest parent, prefer farthest last node
          (node: IPipelineNode) => 1 - node.lastPhase,
          // same highest parent, prefer fewer terminal children if any
          (node: IPipelineNode) => node.children.filter((child) => !child.children.length).length || 100,
          // same highest parent, same number of terminal children, prefer fewer parents
          (node: IPipelineNode) => node.parents.length,
          // same highest parent, same number of terminal children and parents
          (node: IPipelineNode) => 1 - node.children.length,
          // same number of children, so sort by number of grandchildren (more first)
          (node: IPipelineNode) => 1 - sumBy(node.children, (child: IPipelineNode) => child.children.length),
          // great, same number of grandchildren, how about by nearest children, alphabetically by name, why not
          (node: IPipelineNode) => sortBy(node.children, 'phase').map((child: IPipelineNode) => [(child.phase - node.phase), child.name].join('-')).join(':'),
          (node: IPipelineNode) => Number(node.id)
        );
        sortedPhase.forEach((node: IPipelineNode, index: number) => { node.row = index; });
        this.$scope.nodes[phase] = sortedPhase;
      });
      this.fixOverlaps();
    }
  }

  // if any nodes in the same row as a parent node, but not in the immediately preceding phase, inject placeholder nodes
  // so there are no overlapping links
  private fixOverlaps(): void {
    const allNodes = this.$scope.nodes;
    allNodes.forEach((column: IPipelineNode[]) => {
      column.forEach(node => {
        const nonImmediateChildren = node.children.filter(c => c.phase - node.phase > 1 && c.row === node.row);
        nonImmediateChildren.forEach(child => {
          for (let phase = node.phase + 1; phase < child.phase; phase++) {
            if (allNodes[phase].length >= node.row) {
              allNodes[phase].splice(node.row, 0, this.createPlaceholderNode(node.row, phase));
              allNodes[phase].forEach((n: IPipelineNode, index: number) => { n.row = index; });
            }
          }
        });
      });
    });
  }

  private createPlaceholderNode(row: number, phase: number): IPipelineNode {
    return {
      placeholder: true,
      id: UUIDGenerator.generateUuid(),
      name: '',
      parents: [],
      parentIds: [],
      children: [],
      parentLinks: [],
      childLinks: [],
      isActive: false,
      isHighlighted: false,
      row: row,
      phase: phase,
    };
  }

  /**
   * Sets the width of the graph and determines the width available for each label
   */
  private applyPhaseWidth(): void {
    const graphWidth = this.$element.width() - (2 * this.$scope.nodeRadius);
    const phaseOffset = 2 * this.$scope.nodeRadius + this.$scope.labelOffsetX;
    let maxLabelWidth = graphWidth;

    if (this.$scope.phaseCount) {
      maxLabelWidth = (graphWidth / (this.$scope.phaseCount + 1)) - phaseOffset;
    }
    maxLabelWidth = Math.max(this.minLabelWidth, maxLabelWidth);
    this.$scope.maxLabelWidth = maxLabelWidth;
    if (maxLabelWidth === this.minLabelWidth) {
      this.$scope.graphWidth = (this.$scope.phaseCount + 1) * (maxLabelWidth + phaseOffset) + 5 + 'px';
      this.$scope.graphClass = 'small';
    } else {
      this.$scope.graphWidth = '100%';
      this.$scope.graphClass = '';
    }

    // get the saved horizontal scroll position for executions
    if (this.execution) {
      const offsetForId = this.pipelineGraphService.xScrollOffset[this.execution.id] || 0;
      this.$element.scrollLeft(offsetForId);
    }
  }

  private applyNodeHeights(): void {
    const placeholderNode = this.$element.find('g.placeholder div');
    placeholderNode.width(this.$scope.maxLabelWidth);
    this.$scope.graphHeight = 0;
    this.$scope.nodes.forEach((nodes: IPipelineNode[]) => {
      nodes.forEach((node) => {
        const extraLines = node.extraLabelLines ? '<div>x</div>'.repeat(node.extraLabelLines) : '';
        placeholderNode.html(`<a href>${this.$sce.getTrustedHtml(node.name)}${extraLines}</a>`);
        node.height = placeholderNode.height() + this.$scope.rowPadding;
      });
      this.$scope.graphHeight = Math.max(sumBy(nodes, 'height'), this.$scope.graphHeight);
    });
    placeholderNode.empty();
    this.$scope.graphHeight += 3 * this.$scope.graphVerticalPadding;
  }

  private setNodePositions(): void {
    this.$scope.nodes.forEach((nodes: IPipelineNode[], idx: number) => {
      let nodeOffset = this.$scope.graphVerticalPadding;
      nodes.forEach((node, rowNumber) => {
        node.x = (this.$scope.maxLabelWidth + 2 * this.$scope.nodeRadius + this.$scope.labelOffsetX) * idx;
        node.y = nodeOffset;
        nodeOffset += this.$scope.rowHeights[rowNumber];
      });
    });
  }

  private createLinks(): void {
    this.$scope.nodes.forEach((column: IPipelineNode[]) => {
      column.forEach((node) => {
        node.children.forEach((child) => {
          this.linkNodes(child, node);
        });
      });
    });
  }

  private linkNodes(child: IPipelineNode, parent: IPipelineNode): void {
    const link: IPipelineLink = {
      parent: parent,
      child: child,
      line: this.curvedLink({ source: parent, target: child })
    };
    this.setLinkClass(link);
    parent.childLinks.push(link);
    child.parentLinks.push(link);
  }

  private applyAllNodes(): void {
    const flattened = flatten(this.$scope.nodes),
      highlighted = find(flattened, 'isHighlighted'),
      active = find(flattened, 'isActive'),
      base = filter(flattened, {isActive: false, isHighlighted: false});
    this.$scope.allNodes = base;
    if (highlighted) {
      base.push(highlighted);
    }
    if (active) {
      base.push(active);
    }
  }

  private establishRowHeights(): void {
    const rowHeights: number[] = [];
    this.$scope.nodes.forEach((column: IPipelineNode[]) => {
      column.forEach((node, rowNumber) => {
        if (!rowHeights[rowNumber]) {
          rowHeights[rowNumber] = 0;
        }
        rowHeights[rowNumber] = Math.max(rowHeights[rowNumber], node.height);
      });
    });
    this.$scope.rowHeights = rowHeights;
    this.$scope.graphHeight = Math.max(sum(this.$scope.rowHeights) + this.$scope.graphVerticalPadding, this.$scope.minExecutionGraphHeight);
  }

  private updateGraph(statesOnly = false): void {
    if (!statesOnly) {
      this.applyPhasesAndLink(null);
      this.applyPhaseWidth();
      this.applyNodeHeights();
      this.establishRowHeights();
      this.setNodePositions();
      this.createLinks();
    } else {
      this.$scope.nodes.forEach((column: IPipelineNode[]) => {
        column.forEach(node => this.resetLinks(node));
      });
    }
    this.applyAllNodes();
  }

  private resetLinks(node: IPipelineNode): void {
    if (this.execution) { // executions view
      node.isActive = this.viewState.activeStageId === node.index && this.viewState.executionId === this.execution.id;
    } else { // pipeline config view
      if (node.section === 'triggers') {
        node.isActive = this.viewState.section === node.section;
      } else {
        node.isActive = this.viewState.stageIndex === node.index && this.viewState.section === 'stage';
      }
    }
    node.isHighlighted = false;
    node.parentLinks.forEach(link => {
      link.isHighlighted = false;
      this.setLinkClass(link);
    });
    node.childLinks.forEach(link => {
      link.isHighlighted = false;
      this.setLinkClass(link);
    });
  }

  public $onInit(): void {
    // track and save the graph scroll position for executions so it doesn't get reset to
    // zero every second due to repaint.
    this.$element.on('mousewheel', () => {
      if (this.execution) {
        this.pipelineGraphService.xScrollOffset[this.execution.id] = this.$element.scrollLeft();
      }
    });

    this.$scope.warningsPopover = require('./warnings.popover.html');
    this.$scope.nodeRadius = 8;
    this.$scope.rowPadding = 20;
    this.$scope.graphVerticalPadding = 11;
    this.$scope.minExecutionGraphHeight = 40;
    this.$scope.labelOffsetX = this.$scope.nodeRadius + 3;
    this.$scope.labelOffsetY = this.$scope.nodeRadius + 10;
    const graphId = this.pipeline ? this.pipeline.id : this.execution.id;

    this.$scope.nodeClicked = (node: IPipelineNode) => {
      this.onNodeClick(node);
    };

    this.$scope.highlight = (node: IPipelineNode) => {
      if (node.isActive) {
        return;
      }
      node.isHighlighted = true;
      node.parentLinks.forEach((link: IPipelineLink) => {
        link.isHighlighted = true;
        this.setLinkClass(link);
      });
      node.childLinks.forEach((link: IPipelineLink) => {
        link.isHighlighted = true;
        this.setLinkClass(link);
      });
    };

    this.$scope.removeHighlight = (node: IPipelineNode) => {
      if (node.isActive) {
        return;
      }
      node.isHighlighted = false;
      node.parentLinks.forEach((link) => {
        link.isHighlighted = false;
        this.setLinkClass(link);
      });
      node.childLinks.forEach((link) => {
        link.isHighlighted = false;
        this.setLinkClass(link);
      });
    };

    const handleWindowResize = throttle(() => {
      this.$scope.$evalAsync(() => this.updateGraph());
    }, 300);

    const validationSubscription = this.pipelineConfigValidator.subscribe((validations) => {
      this.pipelineValidations = validations;
      this.updateGraph();
    });

    this.updateGraph();
    if (this.shouldValidate) {
      this.$scope.$watch('$ctrl.pipeline', debounce(() => this.pipelineConfigValidator.validatePipeline(this.pipeline), 300), true);
    }
    this.$scope.$watch('$ctrl.viewState', (_newVal: any, oldVal: any) => { if (oldVal) { this.updateGraph(true); } }, true);
    this.$scope.$watch('$ctrl.execution.graphStatusHash', (_newVal: any, oldVal: any) => { if (oldVal) { this.updateGraph(); } });
    this.$(this.$window).bind('resize.pipelineGraph-' + graphId, handleWindowResize);

    this.$scope.$on('$destroy', () => {
      validationSubscription.unsubscribe();
      this.$(this.$window).unbind('resize.pipelineGraph-' + graphId);
    });
  }
}

export class PipelineGraphComponent implements ng.IComponentOptions {
  public bindings: any = {
    pipeline: '<',
    execution: '<',
    viewState: '<',
    onNodeClick: '<',
    shouldValidate: '<',
  };

  public templateUrl: string = require('./pipelineGraph.component.html');
  public controller: any = PipelineGraphController;
}

export const PIPELINE_GRAPH_COMPONENT = 'spinnaker.core.pipeline.config.graph.component';

module(PIPELINE_GRAPH_COMPONENT, [
  PIPELINE_GRAPH_SERVICE,
  PIPELINE_CONFIG_VALIDATOR,
  LABEL_COMPONENT
])
  .component('pipelineGraph', new PipelineGraphComponent());

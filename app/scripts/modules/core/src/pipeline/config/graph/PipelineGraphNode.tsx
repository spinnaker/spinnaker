import * as React from 'react';
import * as ReactGA from 'react-ga';
import * as DOMPurify from 'dompurify';
import * as classNames from 'classnames';
import { BindAll } from 'lodash-decorators';
import { get } from 'lodash';

import { IExecutionStageSummary } from 'core/domain';
import { GroupExecutionPopover } from 'core/pipeline/config/stages/group/GroupExecutionPopover';
import { LabelComponent } from 'core/presentation';
import { Popover } from 'core/presentation/Popover';

import { IPipelineGraphNode } from './pipelineGraph.service';

export interface IPipelineGraphNodeProps {
  isExecution: boolean;
  labelOffsetX: number;
  labelOffsetY: number;
  maxLabelWidth: number;
  nodeClicked: (node: IPipelineGraphNode | IExecutionStageSummary, subIndex?: number) => void;
  highlight: (node: IPipelineGraphNode, highlight: boolean) => void;
  nodeRadius: number;
  node: IPipelineGraphNode;
}

@BindAll()
export class PipelineGraphNode extends React.Component<IPipelineGraphNodeProps> {
  private highlight(): void {
    this.props.highlight(this.props.node, true);
  }

  private removeHighlight(): void {
    this.props.highlight(this.props.node, false);
  }

  private handleClick(): void {
    ReactGA.event({
      category: `Pipeline Graph (${this.props.isExecution ? 'execution' : 'config'})`,
      action: `Node clicked`
    });
    this.props.nodeClicked(this.props.node);
  }

  private subStageClicked(groupStage: IExecutionStageSummary, stage: IExecutionStageSummary): void {
    ReactGA.event({
      category: `Pipeline Graph (${this.props.isExecution ? 'execution' : 'config'})`,
      action: `Grouped stage clicked`
    });
    this.props.nodeClicked(groupStage, stage.index);
  }

  public render() {
    const { labelOffsetX, labelOffsetY, maxLabelWidth, nodeRadius, node } = this.props;

    const isGroup = node.stage && node.stage.type === 'group';
    let stageType = get(node, ['masterStage', 'type'], undefined);
    stageType = stageType || get(node, ['stage', 'activeStageType'], '');
    const circleClassName = classNames(
      `stage-type-${stageType.toLowerCase()}`,
      'execution-marker',
      `execution-marker-${(node.status || '').toLowerCase()}`,
      'graph-node',
      {
        active: node.isActive,
        clickable: !isGroup
      }
    );

    const warningsPopover = (
      <div>
        <p>The following errors may affect the ability to run this pipeline:</p>
        <ul>
          {node.hasWarnings && node.warnings.messages.map((message) => <li key={message} dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(message) }}/>)}
        </ul>
      </div>
    );

    let GraphNode = (
      <g>
        { isGroup && (
          <path
            className={circleClassName}
            fillOpacity={!node.isActive && node.executionStage ? 0.4 : 1}
            transform="translate(-7,-9)"
            d="M8 9l-8-4 8-4 8 4zM14.398 7.199l1.602 0.801-8 4-8-4 1.602-0.801 6.398 3.199zM14.398 10.199l1.602 0.801-8 4-8-4 1.602-0.801 6.398 3.199z"
          />
        )}

        { !isGroup && (
          <circle
            r={nodeRadius}
            className={circleClassName}
            fillOpacity={!node.isActive && node.executionStage ? 0.4 : 1}
            onClick={this.handleClick}
          />
        )}

        { (node.root || node.leaf) && !node.executionStage && !isGroup && (
          <rect
            transform={node.root ? `translate(${nodeRadius * -1},${nodeRadius * -1})` : `translate(0,${nodeRadius * -1})`}
            className={circleClassName}
            height={nodeRadius * 2}
            width={nodeRadius}
            onClick={this.handleClick}
          />
        )}
      </g>
    );

    // Only for pipeline
    if (node.hasWarnings) {
      GraphNode = <Popover placement="bottom" template={warningsPopover}>{GraphNode}</Popover>;
    }

    // Add the group popover to the circle if the node is representative of a group
    // Only executions have a 'stage' property
    if (node.stage && node.stage.type === 'group') {
      GraphNode = <GroupExecutionPopover stage={node.stage} subStageClicked={this.subStageClicked}>{GraphNode}</GroupExecutionPopover>;
    }

    // Render the label differently if there is a custom label component
    let GraphLabel = node.labelComponent ? (
      <div
        className={`execution-stage-label ${!isGroup ? 'clickable' : 'stage-group'} ${(node.status || '').toLowerCase()}`}
        onClick={this.handleClick}
      >
        <LabelComponent stage={node.stage}/>
      </div>
    ) : (
      <div
        className={`label-body node ${!isGroup ? 'clickable' : ''}`}
        onClick={this.handleClick}
      >
        <a>{node.name}</a>
      </div>
    );

    // Add the group popover to the label if the node is representative of a group
    if (node.stage && node.stage.type === 'group') {
      GraphLabel = <GroupExecutionPopover stage={node.stage} subStageClicked={this.subStageClicked}>{GraphLabel}</GroupExecutionPopover>
    }

    // Wrap all the label html in a foreignObject to make SVG happy
    GraphLabel = (
      <foreignObject
        width={maxLabelWidth}
        height="34"
        transform={`translate(${labelOffsetX}, ${node.leaf && !node.executionStage ? -8 : labelOffsetY * -1})`}
      >
        {GraphLabel}
      </foreignObject>
    );

    return (
      <g
        onMouseEnter={this.highlight}
        onMouseLeave={this.removeHighlight}
        style={{pointerEvents: 'bounding-box'}}
      >
        {GraphNode}
        {GraphLabel}
      </g>
    );
  }
}

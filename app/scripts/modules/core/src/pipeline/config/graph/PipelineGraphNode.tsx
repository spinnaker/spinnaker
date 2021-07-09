import classNames from 'classnames';
import { get } from 'lodash';
import React from 'react';

import { IExecutionStageSummary } from '../../../domain';
import { IPipelineGraphNode } from './pipelineGraph.service';
import { LabelComponent, Markdown } from '../../../presentation';
import { Popover } from '../../../presentation/Popover';
import { GroupExecutionPopover } from '../stages/group/GroupExecutionPopover';
import { logger } from '../../../utils';

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

export class PipelineGraphNode extends React.Component<IPipelineGraphNodeProps> {
  private highlight = (): void => {
    this.props.highlight(this.props.node, true);
  };

  private removeHighlight = (): void => {
    this.props.highlight(this.props.node, false);
  };

  private handleClick = (): void => {
    logger.log({
      category: `Pipeline Graph (${this.props.isExecution ? 'execution' : 'config'})`,
      action: `Node clicked`,
    });
    this.props.nodeClicked(this.props.node);
  };

  private subStageClicked = (groupStage: IExecutionStageSummary, stage: IExecutionStageSummary): void => {
    logger.log({
      category: `Pipeline Graph (${this.props.isExecution ? 'execution' : 'config'})`,
      action: `Grouped stage clicked`,
    });
    this.props.nodeClicked(groupStage, stage.index);
  };

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
        clickable: !isGroup,
      },
    );

    const warningsPopover = (
      <div>
        <p>The following errors may affect the ability to run this pipeline:</p>
        <ul>
          {node.hasWarnings &&
            node.warnings.messages.map((message) => (
              <li key={message}>
                <Markdown message={message} trim={true} />
              </li>
            ))}
        </ul>
      </div>
    );

    let GraphNode = (
      <g>
        {isGroup && (
          <path
            className={circleClassName}
            fillOpacity={!node.isActive && node.executionStage ? 0.4 : 1}
            transform="translate(-7,-9)"
            d="M8 9l-8-4 8-4 8 4zM14.398 7.199l1.602 0.801-8 4-8-4 1.602-0.801 6.398 3.199zM14.398 10.199l1.602 0.801-8 4-8-4 1.602-0.801 6.398 3.199z"
          />
        )}

        {!isGroup && (
          <circle
            r={nodeRadius}
            className={circleClassName}
            fillOpacity={!node.isActive && node.executionStage ? 0.4 : 1}
          />
        )}

        {(node.root || node.leaf) && !node.executionStage && !isGroup && (
          <rect
            transform={
              node.root ? `translate(${nodeRadius * -1},${nodeRadius * -1})` : `translate(0,${nodeRadius * -1})`
            }
            className={circleClassName}
            height={nodeRadius * 2}
            width={nodeRadius}
          />
        )}
      </g>
    );

    // Only for pipeline
    if (node.hasWarnings) {
      GraphNode = (
        <Popover placement="bottom" template={warningsPopover}>
          {GraphNode}
        </Popover>
      );
    }

    // Render the label differently if there is a custom label component
    let GraphLabel = node.labelComponent ? (
      <div
        className={`execution-stage-label ${!isGroup ? 'clickable' : 'stage-group'} ${(
          node.status || ''
        ).toLowerCase()}`}
      >
        <LabelComponent stage={node.stage} />
      </div>
    ) : (
      <div className={`label-body node ${!isGroup ? 'clickable' : ''}`}>
        <a>{node.name}</a>
      </div>
    );

    // Wrap all the label html in a foreignObject to make SVG happy
    GraphLabel = (
      <g transform={`translate(${labelOffsetX}, ${node.leaf && !node.executionStage ? -8 : labelOffsetY * -1})`}>
        <foreignObject width={maxLabelWidth} height={node.height}>
          {GraphLabel}
        </foreignObject>
      </g>
    );

    let NodeContents = (
      <>
        {GraphNode}
        {GraphLabel}
      </>
    );

    if (node.stage && node.stage.type === 'group') {
      NodeContents = (
        <GroupExecutionPopover
          stage={node.stage}
          subStageClicked={this.subStageClicked}
          width={maxLabelWidth}
          svgMode={true}
        >
          {NodeContents}
        </GroupExecutionPopover>
      );
    }

    return (
      <g
        onMouseEnter={this.highlight}
        onMouseLeave={this.removeHighlight}
        onClick={this.handleClick}
        style={{ cursor: 'pointer', pointerEvents: 'all' }}
      >
        {NodeContents}
      </g>
    );
  }
}

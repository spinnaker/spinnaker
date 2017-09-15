import * as React from 'react';
import * as ReactGA from 'react-ga';
import * as DOMPurify from 'dompurify';
import * as classNames from 'classnames';
import autoBindMethods from 'class-autobind-decorator';
import { get } from 'lodash';

import { LabelComponent } from 'core/presentation';
import { Popover } from 'core/presentation/Popover';

import { IPipelineGraphNode } from './pipelineGraph.service';

export interface IPipelineGraphNodeProps {
  isExecution: boolean;
  labelOffsetX: number;
  labelOffsetY: number;
  maxLabelWidth: number;
  nodeClicked: (node: IPipelineGraphNode) => void;
  highlight: (node: IPipelineGraphNode, highlight: boolean) => void;
  nodeRadius: number;
  node: IPipelineGraphNode;
}

@autoBindMethods
export class PipelineGraphNode extends React.Component<IPipelineGraphNodeProps> {
  private highlight() {
    this.props.highlight(this.props.node, true);
  }

  private removeHighlight() {
    this.props.highlight(this.props.node, false);
  }

  private handleClick() {
    ReactGA.event({
      category: `Pipeline Graph (${this.props.isExecution ? 'execution' : 'config'})`,
      action: `Node clicked`
    });
    this.props.nodeClicked(this.props.node);
  }

  public render() {
    const { labelOffsetX, labelOffsetY, maxLabelWidth, nodeRadius, node } = this.props;

    const masterStageType = get(node, ['masterStage', 'type'], '');
    const circleClassName = classNames(
      'clickable',
      `stage-type-${masterStageType.toLowerCase()}`,
      'execution-marker',
      `execution-marker-${(node.status || '').toLowerCase()}`,
      { active: node.isActive }
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
      <circle
        r={nodeRadius}
        className={circleClassName}
        fillOpacity={!node.isActive && node.executionStage ? 0.4 : 1}
        onMouseEnter={this.highlight}
        onMouseLeave={this.removeHighlight}
        onClick={this.handleClick}
      />

      { (node.root || node.leaf) && !node.executionStage && (
        <rect
          transform={node.root ? `translate(${nodeRadius * -1},${nodeRadius * -1})` : `translate(0,${nodeRadius * -1})`}
          className="clickable"
          height={nodeRadius * 2}
          width={nodeRadius}
          onMouseEnter={this.highlight}
          onMouseLeave={this.removeHighlight}
          onClick={this.handleClick}
        />
      )}
    </g>
    );

    if (node.hasWarnings) {
      GraphNode = <Popover placement="bottom" template={warningsPopover}>{GraphNode}</Popover>;
    }

    const GraphLabel = (
      <foreignObject
        width={maxLabelWidth}
        height="100"
        transform={`translate(${labelOffsetX}, ${node.leaf && !node.executionStage ? -8 : labelOffsetY * -1})`}
      >
        { node.labelComponent && (
          <div
            className={`execution-stage-label clickable ${(node.status || '').toLowerCase()}`}
            onMouseEnter={this.highlight}
            onMouseLeave={this.removeHighlight}
            style={{height: node.height + 'px'}}
            onClick={this.handleClick}
          >
            <LabelComponent stage={node as any}/>
          </div>
        )}
        { !node.labelComponent && (
          <div
            className="label-body node clickable"
            onMouseEnter={this.highlight}
            onMouseLeave={this.removeHighlight}
            onClick={this.handleClick}
          >
            <a>{node.name}</a>
          </div>
        )}
      </foreignObject>
    );

    return (
      <g>
        {GraphNode}
        {GraphLabel}
      </g>
    );
  }
}

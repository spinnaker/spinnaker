import classNames from 'classnames';
import React from 'react';

import { IPipelineGraphLink } from './pipelineGraph.service';

export interface IPiplineGraphLinkProps {
  link: IPipelineGraphLink;
  x: number;
  y: number;
}

export class PipelineGraphLink extends React.Component<IPiplineGraphLinkProps> {
  public render() {
    const { link, x, y } = this.props;
    const { child, isHighlighted, parent } = link;
    const className = classNames('link', {
      highlighted: isHighlighted,
      active: child.isActive || parent.isActive,
      target: !child.executionStage && child.isActive,
      source: !child.executionStage && !child.isActive,
      'has-status': child.executionStage,
      [`${(child.status || '').toLowerCase()}`]: child.executionStage && child.hasNotStarted,
      [`${(parent.status || '').toLowerCase()}`]: child.executionStage && !child.hasNotStarted,
    });

    return <path d={link.line} className={className} transform={`translate(${0 - x}, ${0 - y})`} />;
  }
}

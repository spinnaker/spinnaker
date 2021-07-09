import classNames from 'classnames';
import React from 'react';

import { ITaskStep } from '../domain';

export interface IStatusGlyphProps {
  item: ITaskStep;
}

export class StatusGlyph extends React.Component<IStatusGlyphProps> {
  public render() {
    const { item } = this.props;
    const className = classNames([
      'glyphicon',
      'status-glyph',
      {
        'glyphicon-time': item.hasNotStarted,
        'glyphicon-play': item.isRunning,
        'fa fa-check': item.isCompleted,
        'fa fa-exclamation-triangle': item.isFailed || item.status === 'TERMINATED',
        'glyphicon-stop': item.isCanceled,
        'glyphicon-pause': item.isSuspended || item.isPaused,
      },
    ]);

    return <span className={className} />;
  }
}

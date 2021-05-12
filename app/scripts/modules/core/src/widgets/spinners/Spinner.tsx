import classnames from 'classnames';
import React from 'react';

export interface ISpinnerProps {
  size?: 'nano' | 'small' | 'medium' | 'large';
  message?: string;
  fullWidth?: boolean;
}

export class Spinner extends React.Component<ISpinnerProps> {
  public getBarRows(): React.ReactNode[] {
    const { size } = this.props;
    let count = 3;

    if (size) {
      if (size === 'nano') {
        count = 1;
      } else if (size === 'large') {
        count = 5;
      }
    }

    const rows = [];
    let i: number;
    for (i = 0; i < count; i++) {
      rows.push(<div key={i} className="bar" />);
    }
    return rows;
  }

  public render(): React.ReactElement<Spinner> {
    const { size, message, fullWidth } = this.props;
    const messageClassNames = `message color-text-accent ${size === 'medium' ? 'heading-4' : 'heading-2'}`;

    const messageNode = ['medium', 'large'].includes(size) && (
      <div className={messageClassNames}>{message || 'Loading ...'}</div>
    );

    const bars = ['medium', 'large'].includes(size) ? (
      <div className="bars">{this.getBarRows()}</div>
    ) : (
      this.getBarRows()
    );

    return (
      <div className={classnames('load', size || 'small', { 'full-width': fullWidth })}>
        {messageNode}
        {bars}
      </div>
    );
  }
}

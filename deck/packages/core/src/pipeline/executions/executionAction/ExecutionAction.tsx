import classNames from 'classnames';
import React from 'react';

import { Tooltip } from '../../../presentation';
import { noop } from '../../../utils';

export interface IExecutionActionProps {
  children: React.ReactNode;
  disabled?: boolean;
  handleClick?: (e: React.MouseEvent<HTMLElement>) => void;
  style?: React.CSSProperties;
  tooltipText?: string;
}

export class ExecutionAction extends React.Component<IExecutionActionProps> {
  public static defaultProps = {
    disabled: false,
    handleClick: noop,
    style: {},
    tooltipText: '',
  };

  public render() {
    const { children, disabled, handleClick, style, tooltipText } = this.props;
    const linkClassNames = classNames('btn', 'btn-xs', 'btn-link', disabled && 'disabled');

    const actionNode = (
      <h4 style={style}>
        <a className={linkClassNames} onClick={handleClick} aria-disabled={disabled}>
          {children}
        </a>
      </h4>
    );

    if (tooltipText) {
      return <Tooltip value={tooltipText}>{actionNode}</Tooltip>;
    }

    return actionNode;
  }
}

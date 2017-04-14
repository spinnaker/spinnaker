import * as React from 'react';
import { OverlayTrigger, Tooltip as BSTooltip } from 'react-bootstrap';

interface ITooltipProps {
  value?: string;
  template?: JSX.Element;
  placement?: 'top' | 'bottom' | 'left' | 'right';
}

export class Tooltip extends React.Component<ITooltipProps, void> {
  public static defaultProps: Partial<ITooltipProps> = {
    placement: 'top',
    value: ''
  };

  public render() {
    let tooltip = <BSTooltip id={this.props.value}>{this.props.value}</BSTooltip>;
    if (this.props.template) {
      tooltip = <BSTooltip id={this.props.value}>{this.props.template}</BSTooltip>;
    }

    return (
      <OverlayTrigger placement={this.props.placement} overlay={tooltip}>
        {this.props.children}
      </OverlayTrigger>
    );
  }
}

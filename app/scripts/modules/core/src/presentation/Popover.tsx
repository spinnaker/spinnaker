import React from 'react';
import { OverlayTrigger, Popover as BSPopover } from 'react-bootstrap';

import { Markdown } from './Markdown';
import { Placement } from './Placement';

type onEnterType = (element: HTMLElement) => void;
export interface IPopoverProps {
  value?: string;
  template?: JSX.Element;
  placement?: Placement;
  onEnter?: onEnterType;
}

export class Popover extends React.Component<IPopoverProps> {
  public static defaultProps: Partial<IPopoverProps> = {
    placement: 'top',
    value: '',
  };

  private onEntering = (element: HTMLElement): void => {
    const { onEnter } = this.props;
    if (onEnter) {
      onEnter(element);
    }
  };

  public render() {
    const { value, template, placement, children } = this.props;
    let popover = (
      <BSPopover id={value}>
        <Markdown message={value} />
      </BSPopover>
    );
    if (template) {
      popover = <BSPopover id={value}>{template}</BSPopover>;
    }

    return (
      <OverlayTrigger onEntering={this.onEntering} placement={placement} overlay={popover}>
        {children}
      </OverlayTrigger>
    );
  }
}

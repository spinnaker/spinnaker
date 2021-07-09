import React from 'react';
import { OverlayTrigger, Tooltip as BSTooltip } from 'react-bootstrap';

import { Markdown } from './Markdown';
import { Placement } from './Placement';

export interface ITooltipProps {
  id?: string;
  value?: string;
  template?: JSX.Element;
  placement?: Placement;
  delayShow?: number;
}

export class Tooltip extends React.Component<ITooltipProps> {
  public static defaultProps: Partial<ITooltipProps> = {
    placement: 'top',
    value: '',
  };

  private popover: any; // OverlayTrigger does not expose hide() in it's type definition

  private handleRef = (popover: OverlayTrigger): void => {
    if (popover) {
      this.popover = popover;
    }
  };

  public componentWillReceiveProps(nextProps: ITooltipProps): void {
    if (this.popover) {
      if (this.props.value && !nextProps.value) {
        this.popover.hide();
      }
      if (!this.props.value && nextProps.value) {
        this.popover.show();
      }
    }
  }

  public render() {
    const { delayShow, id, placement, template, value } = this.props;
    const useId = id || value || 'tooltip';

    let tooltip = (
      <BSTooltip id={useId}>
        <Markdown message={value} />
      </BSTooltip>
    );
    if (template) {
      tooltip = <BSTooltip id={useId}>{template}</BSTooltip>;
    }

    const hasValue = (value && value.length > 0) || template;
    const trigger = hasValue ? ['hover', 'focus'] : [];

    return (
      <OverlayTrigger
        ref={this.handleRef}
        delayShow={delayShow}
        placement={placement}
        overlay={tooltip}
        trigger={trigger}
      >
        {this.props.children}
      </OverlayTrigger>
    );
  }
}

import * as React from 'react';
import {Overlay, Popover} from 'react-bootstrap';
import autoBindMethods from 'class-autobind-decorator';

import {Placement} from 'core/presentation/Placement';
import {UUIDGenerator} from '../utils/uuid.service';

export interface IProps {
  value?: string;
  template?: JSX.Element;
  placement?: Placement;
  id?: string;
}

export interface IState {
  popoverIsOpen: boolean;
  target?: any;
}

@autoBindMethods
export class HoverablePopover extends React.Component<IProps, IState> {
  public static defaultProps: Partial<IProps> = {
    placement: 'top',
    id: UUIDGenerator.generateUuid()
  };

  private popoverCancel: number;

  constructor(props: IProps) {
    super(props);
    this.state = {
      popoverIsOpen: false,
    };
  }

  public componentWillUnmount() {
    this.hidePopover();
  }

  private showPopover(e: React.MouseEvent<HTMLElement>): void {
    this.clearPopoverCancel();
    this.setState({popoverIsOpen: true, target: e.target});
  }

  private deferHidePopover(): void {
    this.popoverCancel = window.setTimeout(() => this.hidePopover(), 300);
  }

  private hidePopover(): void {
    this.clearPopoverCancel();
    this.setState({popoverIsOpen: false});
  }

  private clearPopoverCancel(): void {
    if (this.popoverCancel) {
      window.clearTimeout(this.popoverCancel);
    }
    this.popoverCancel = null;
  }

  public render() {
    const PopoverContents = this.props.template;
    return (
      <span onMouseEnter={this.showPopover} onMouseLeave={this.deferHidePopover}>
        {this.props.children}
        <Overlay show={this.state.popoverIsOpen} placement={this.props.placement} onEnter={this.clearPopoverCancel} target={this.state.target}>
          <Popover onMouseOver={this.clearPopoverCancel} onMouseLeave={this.hidePopover} onBlur={this.hidePopover} id={this.props.id}>
            {PopoverContents}
          </Popover>
        </Overlay>
      </span>
    );
  }
}

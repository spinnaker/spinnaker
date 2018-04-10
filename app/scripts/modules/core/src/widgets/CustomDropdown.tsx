import * as React from 'react';
import { BindAll } from 'lodash-decorators';

export interface IToggleProps {
  bsRole: string;
  onClick?: React.EventHandler<React.MouseEvent<HTMLAnchorElement>>;
}

@BindAll()
export class CustomToggle extends React.Component<IToggleProps, {}> {
  constructor(props: IToggleProps) {
    super(props);
  }

  private handleClick(e: React.MouseEvent<HTMLAnchorElement>): void {
    e.preventDefault();
    this.props.onClick(e);
  }

  public render() {
    return (
      <a onClick={this.handleClick} className="remove-border-top">
        {this.props.children}
      </a>
    );
  }
}

export interface IMenuProps {
  bsRole: string;
}

export interface IMenuState {
  value: string;
}
export class CustomMenu extends React.Component<IMenuProps, IMenuState> {
  constructor(props: IMenuProps) {
    super(props);
    this.state = { value: '' };
  }

  public render() {
    const { children } = this.props;

    return (
      <ul className="dropdown-menu" style={{ padding: '' }}>
        {React.Children.toArray(children)}
      </ul>
    );
  }
}

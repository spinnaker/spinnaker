import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

export interface ToggleProps {
  bsRole: string;
  onClick?: React.EventHandler<React.MouseEvent<HTMLAnchorElement>>;
}

@autoBindMethods
export class CustomToggle extends React.Component<ToggleProps, {}> {
  constructor(props: ToggleProps) {
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

export interface MenuProps {
  bsRole: string;
}

export interface MenuState {
  value: string;
}
export class CustomMenu extends React.Component<MenuProps, MenuState> {
  constructor(props: MenuProps) {
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

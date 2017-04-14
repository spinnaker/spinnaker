import * as React from 'react';

interface ToggleProps {
  bsRole: string;
  onClick?: React.EventHandler<React.MouseEvent<HTMLAnchorElement>>;
}
export class CustomToggle extends React.Component<ToggleProps, {}> {
  constructor(props: ToggleProps) {
    super(props);
  }

  public handleClick (e: React.MouseEvent<HTMLAnchorElement>) {
    e.preventDefault();
    this.props.onClick(e);
  }

  public render() {
    return (
      <a onClick={(e) => this.handleClick(e)} className="remove-border-top">
        {this.props.children}
      </a>
    );
  }
};

interface MenuProps {
  bsRole: string;
}

interface MenuState {
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

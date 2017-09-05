import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

const Logo = require('./logo.svg');

export interface ISpinnerProps {
  size?: 'nano' | 'small' | 'medium' | 'large' | 'page';
  message?: string;
  postnote?: string;
}

@autoBindMethods
export class Spinner extends React.Component<ISpinnerProps> {

  public getBarRows(): Array<React.ReactNode> {
    const { size } = this.props;
    let count = 3;

    if (size === 'nano') {
      count = 1;
    } else if (size.match(/large|page/)) {
      count = 5;
    }

    const rows = [];
    let i: number;
    for (i = 0; i < count; i++) {
      rows.push(<div className="bar" />)
    }
    return rows;
  }

  public render(): React.ReactElement<Spinner> {
    const { size, message, postnote } = this.props;
    const mainClassNames = `load ${size || 'small'} ${size === 'page' && 'large vertical center'}`;
    const messageClassNames = `message color-text-accent ${size === 'medium' ? 'heading-4' : 'heading-2'}`;

    const logo = (size === 'page' && <Logo />);

    const messageNode = ['medium', 'large', 'page'].includes(size) &&
      <div className={messageClassNames}>{message || 'Loading ...'}</div>;

    const bars = ['medium', 'large', 'page'].includes(size) ? (
      <div className="bars">
        {this.getBarRows()}
      </div>
      ) :
      this.getBarRows();

    const postnoteNode = (size === 'page' &&
      <div className="postnote">{postnote}</div>);

    return (
      <div className={mainClassNames}>
        {logo}
        {messageNode}
        {bars}
        {postnoteNode}
      </div>
    );
  }
}

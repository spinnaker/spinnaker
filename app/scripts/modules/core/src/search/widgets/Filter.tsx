import * as React from 'react';
import * as classNames from 'classnames';
import autoBindMethods from 'class-autobind-decorator';

import { IFilterType } from './SearchFilterTypeRegistry';

export interface IFilterProps {
  filterType: IFilterType;
  isActive: boolean;
  onClick?: (key: string) => void;
  onKeyUp?: (event: React.KeyboardEvent<HTMLElement>) => void;
  onMouseDown?: (event: React.MouseEvent<HTMLElement>) => void;
}

@autoBindMethods
export class Filter extends React.Component<IFilterProps> {

  public static defaultProps: Partial<IFilterProps> = {
    onClick: () => {},
    onKeyUp: () => {},
    onMouseDown: () => {}
  };

  private handleClick(): void {
    const { filterType, onClick } = this.props;
    const { text, modifier } = filterType;
    onClick([text, modifier].join('|'));
  }

  private handleKeyUp(event: React.KeyboardEvent<HTMLElement>): void {
    this.props.onKeyUp(event);
  }

  private handleMouseDown(event: React.MouseEvent<HTMLElement>): void {
    this.props.onMouseDown(event);
  }

  public render(): React.ReactElement<Filter> {

    const { isActive } = this.props;
    const className = classNames({
      'filter': true,
      'filter--focus': isActive,
      'filter--blur': !isActive
    });

    const { modifier, text } = this.props.filterType;
    return (
      <div
        role="option"
        className={className}
        onClick={this.handleClick}
        onKeyUp={this.handleKeyUp}
        onMouseDown={this.handleMouseDown}
      >
        <div className="filter__text">{text}</div>
        <div className="filter__modifier">[{modifier.toLocaleUpperCase()}:]</div>
      </div>
    );
  }
}

import classNames from 'classnames';
import React from 'react';

import { IFilterType } from './SearchFilterTypeRegistry';

export interface IFilterProps {
  filterType: IFilterType;
  isActive: boolean;
  onClick?: (key: string) => void;
  onKeyUp?: (event: React.KeyboardEvent<HTMLElement>) => void;
  onMouseDown?: (event: React.MouseEvent<HTMLElement>) => void;
}

export class Filter extends React.Component<IFilterProps> {
  public static defaultProps: Partial<IFilterProps> = {
    onClick: () => {},
    onKeyUp: () => {},
    onMouseDown: () => {},
  };

  private handleClick = (): void => {
    const { filterType, onClick } = this.props;
    const { name, key } = filterType;
    onClick([name, key].join('|'));
  };

  private handleKeyUp = (event: React.KeyboardEvent<HTMLElement>): void => {
    this.props.onKeyUp(event);
  };

  private handleMouseDown = (event: React.MouseEvent<HTMLElement>): void => {
    this.props.onMouseDown(event);
  };

  public render(): React.ReactElement<Filter> {
    const { isActive } = this.props;
    const className = classNames({
      filter: true,
      'filter--focus': isActive,
      'filter--blur': !isActive,
    });

    const { key, name } = this.props.filterType;
    return (
      <div
        role="option"
        className={className}
        onClick={this.handleClick}
        onKeyUp={this.handleKeyUp}
        onMouseDown={this.handleMouseDown}
      >
        <div className="filter__text">{name}</div>
        <div className="filter__modifier">
          [{key.toLocaleUpperCase()}
          :]
        </div>
      </div>
    );
  }
}

import * as React from 'react';
import * as classNames from 'classnames';
import autoBindMethods from 'class-autobind-decorator';

import { IFilterType } from './SearchFilterTypeRegistry';

export interface IFilterProps {
  filterType: IFilterType;
  isActive: boolean;
  onKeyUp?: (event: React.KeyboardEvent<HTMLElement>) => void;
}

@autoBindMethods
export class Filter extends React.Component<IFilterProps> {

  public static defaultProps: Partial<IFilterProps> = {
    onKeyUp: () => {}
  };

  private handleKeyUp(event: React.KeyboardEvent<HTMLElement>): void {
    this.props.onKeyUp(event);
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
        onKeyUp={this.handleKeyUp}
      >
        <div className="filter__text">{text}</div>
        <div className="filter__modifier">[{modifier.toLocaleUpperCase()}:]</div>
      </div>
    );
  }
}

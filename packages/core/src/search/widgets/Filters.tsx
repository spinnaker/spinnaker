import classNames from 'classnames';
import React from 'react';

import { Filter } from './Filter';
import { IFilterType } from './SearchFilterTypeRegistry';
import { Key } from '../../widgets/Keys';

import './filterlist.less';

export interface IFiltersLayout {
  header: string;
  filterTypes: IFilterType[];
}

export interface IFiltersProps {
  activeFilter: IFilterType;
  layouts: IFiltersLayout[];
  isOpen: boolean;
  filterClicked?: (filter: Filter) => void;
  onKeyUp?: (key: Key) => void;
  onMouseDown?: () => void;
}

export class Filters extends React.Component<IFiltersProps> {
  public static defaultProps: Partial<IFiltersProps> = {
    filterClicked: () => {},
    onKeyUp: () => {},
    onMouseDown: () => {},
  };

  private filters: Filter[] = [];

  private refCallback = (filter: Filter): void => {
    this.filters.push(filter);
  };

  private handleClick = (str: string): void => {
    const clickedFilter = this.filters.find((filter: Filter) => {
      const { name, key } = filter.props.filterType;
      return str === [name, key].join('|');
    });
    this.props.filterClicked(clickedFilter);
  };

  private handleKeyUp = (event: React.KeyboardEvent<HTMLElement>): void => {
    switch (event.key) {
      case Key.UP_ARROW:
      case Key.DOWN_ARROW:
        this.props.onKeyUp(event.key);
        break;
    }
  };

  private handleMouseDown = (): void => {
    this.props.onMouseDown();
  };

  private generateFilterElement(filterType: IFilterType): JSX.Element {
    const { name, key } = filterType;
    return (
      <Filter
        key={[name, key].join('|')}
        ref={this.refCallback}
        filterType={filterType}
        isActive={this.props.activeFilter.key === key}
        onClick={this.handleClick}
        onKeyUp={this.handleKeyUp}
        onMouseDown={this.handleMouseDown}
      />
    );
  }

  private renderFilterLayout(layout: IFiltersLayout): JSX.Element {
    const { header, filterTypes } = layout;
    const types = (filterTypes || []).map((type: IFilterType) => this.generateFilterElement(type));
    return (
      <div key={[header, filterTypes.length].join('|')}>
        <div className="filter-list__header">{header}</div>
        <div role="listbox">{types}</div>
      </div>
    );
  }

  public render(): React.ReactElement<Filters> {
    const { layouts, isOpen } = this.props;
    const className = classNames({
      'filter-list': true,
      'filter-list__open': isOpen,
      'filter-list__closed': !isOpen,
    });

    const menuLayouts = (layouts || []).map((layout: IFiltersLayout) => this.renderFilterLayout(layout));
    return <div className={className}>{menuLayouts}</div>;
  }
}

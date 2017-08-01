import * as React from 'react';
import * as classNames from 'classnames';
import autoBindMethods from 'class-autobind-decorator';

import { Key } from 'core/widgets/Keys';
import { Filter } from './Filter';
import { IFilterType } from './SearchFilterTypeRegistry';

import './filterlist.less';

export interface IFiltersLayout {
  header: string;
  filterTypes: IFilterType[];
}

export interface IFiltersProps {
  activeFilter: IFilterType;
  layouts: IFiltersLayout[];
  isOpen: boolean;
  onFilterChange?: (key: Key) => void;
}

@autoBindMethods
export class Filters extends React.Component<IFiltersProps> {

  public static defaultProps: Partial<IFiltersProps> = {
    onFilterChange: () => {}
  };

  private handleKeyUp(event: React.KeyboardEvent<HTMLElement>): void {
    switch (event.key) {
      case Key.UP_ARROW:
      case Key.DOWN_ARROW:
        this.props.onFilterChange(event.key);
        break;
    }
  }

  private generateFilterElement(filterType: IFilterType): JSX.Element {

    const { text, modifier } = filterType;
    return (
      <Filter
        key={[text, modifier].join('|')}
        filterType={filterType}
        isActive={this.props.activeFilter.modifier === modifier}
        onKeyUp={this.handleKeyUp}
      />
    );
  }

  private renderFilterLayout(layout: IFiltersLayout): JSX.Element {

    const { header, filterTypes } = layout;
    const types = (filterTypes || []).map((type: IFilterType) => this.generateFilterElement(type));
    return (
      <div key={[header, filterTypes.length].join('|')}>
        <div className="filter-list__header">{header}</div>
        <div role="listbox">
          {types}
        </div>
      </div>
    );
  }

  public render(): React.ReactElement<Filters> {

    const { layouts, isOpen } = this.props;
    const className = classNames({
      'filter-list': true,
      'filter-list__open': isOpen,
      'filter-list__closed': !isOpen
    });

    const menuLayouts = (layouts || []).map((layout: IFiltersLayout) => this.renderFilterLayout(layout));
    return (
      <div className={className}>
        {menuLayouts}
      </div>
    );
  }
}

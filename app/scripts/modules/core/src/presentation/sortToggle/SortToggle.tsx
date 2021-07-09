import classNames from 'classnames';
import React from 'react';

import { noop } from '../../utils';

export interface ISortToggleProps {
  sortKey: string;
  label: string;
  onChange?: (newVal: string) => void;
  currentSort: string;
}

export class SortToggle extends React.Component<ISortToggleProps> {
  public static defaultProps: Partial<ISortToggleProps> = {
    onChange: noop,
  };

  private isSortKey(): boolean {
    const { currentSort, sortKey } = this.props;
    const field = currentSort;
    return field === sortKey || field === '-' + sortKey;
  }

  private isReverse(): boolean {
    return this.props.currentSort && this.props.currentSort.startsWith('-');
  }

  private setSortKey = (event: React.MouseEvent<HTMLElement>): void => {
    const { sortKey, onChange } = this.props;
    event.preventDefault();
    const predicate = this.isSortKey() && this.isReverse() ? '' : '-';
    onChange(predicate + sortKey);
  };

  public render() {
    const isSortKey = this.isSortKey();
    const className = classNames({
      'inactive-sort-key': !isSortKey,
      'sort-toggle': true,
      clickable: true,
    });
    return (
      <span className={className} onClick={this.setSortKey}>
        {this.props.label}
        <a>
          {(!this.isReverse() || !isSortKey) && <span className="glyphicon glyphicon-Down-triangle" />}
          {this.isReverse() && isSortKey && <span className="glyphicon glyphicon-Up-triangle" />}
        </a>
      </span>
    );
  }
}

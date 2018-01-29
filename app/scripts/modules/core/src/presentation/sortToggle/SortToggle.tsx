import * as React from 'react';
import * as classNames from 'classnames';
import { BindAll } from 'lodash-decorators';

import { noop } from 'core/utils';

export interface ISortToggleProps {
  sortKey: string;
  label: string;
  onChange?: (newVal: string) => void;
  model: { key: string };
}

@BindAll()
export class SortToggle extends React.Component<ISortToggleProps> {

  public static defaultProps: Partial<ISortToggleProps> = {
    onChange: noop,
  };

  private isSortKey(): boolean {
    const { model, sortKey } = this.props;
    const field = model.key;
    return field === sortKey || field === '-' + sortKey;
  }

  private isReverse(): boolean {
    return this.props.model.key && this.props.model.key.startsWith('-');
  }

  private setSortKey(event: React.MouseEvent<HTMLElement>): void {
    const { sortKey, onChange } = this.props;
    event.preventDefault();
    const predicate = this.isSortKey() && this.isReverse() ? '' : '-';
    onChange(predicate + sortKey);
  }

  public render() {
    const isSortKey = this.isSortKey();
    const className = classNames({
      'inactive-sort-key': !isSortKey,
      'sort-toggle': true,
      clickable: true,
    });
    return (
      <span
        className={className}
        onClick={this.setSortKey}
      >
        {this.props.label}
        <a>
          {(!this.isReverse() || !isSortKey) && (
            <span className="glyphicon glyphicon-Down-triangle"/>
          )}
          {(this.isReverse() && isSortKey) && (
            <span className="glyphicon glyphicon-Up-triangle"/>
          )}
        </a>
      </span>
    );
  }

}

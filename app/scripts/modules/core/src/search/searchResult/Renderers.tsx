import { kebabCase, startCase } from 'lodash';
import React from 'react';

import { AccountTag } from '../../account';
import { IInstanceCounts } from '../../domain';
import { HealthCounts } from '../../healthCounts';
import { Spinner } from '../../widgets';

export interface ISearchColumn {
  key: string;
  label?: string;
  notSortable?: boolean;
  scopeField?: boolean;
}

/****** Layout Renderers ******/

export class SearchTableHeader extends React.Component {
  public render() {
    return <div className="table-header">{this.props.children}</div>;
  }
}

export class SearchTableRow extends React.Component {
  public render() {
    return <div className="table-row small">{this.props.children}</div>;
  }
}

export class SearchTableBody extends React.Component {
  public render() {
    return <div className="table-contents flex-fill">{this.props.children}</div>;
  }
}

/****** Cell Renderers ******/

const colClass = (key: string) => `col-${kebabCase(key)}`;

export class HeaderCell extends React.Component<{ col: ISearchColumn }> {
  public render() {
    const { col } = this.props;
    return <div className={colClass(col.key)}>{col.label || startCase(col.key)}</div>;
  }
}

export interface ICellRendererProps {
  item: any;
  col: ISearchColumn;
  defaultValue?: string;
}

export class BasicCell extends React.Component<ICellRendererProps> {
  public render() {
    const { item, col, defaultValue, children } = this.props;
    return <div className={colClass(col.key)}>{children || item[col.key] || defaultValue}</div>;
  }
}

export class HrefCell extends React.Component<ICellRendererProps> {
  public render() {
    const { item, col } = this.props;
    return (
      <div className={colClass(col.key)}>
        <a href={item.href}>{item[col.key]}</a>
      </div>
    );
  }
}

export class AccountCell extends React.Component<ICellRendererProps> {
  public render() {
    const { item, col } = this.props;

    const value: string | string[] = item[col.key];
    if (!value) {
      return <div className={colClass(col.key)}>-</div>;
    }

    const accounts = (Array.isArray(value) ? value : value.split(',')).sort();
    return (
      <div className={colClass(col.key)}>
        {accounts.map((account: string) => (
          <AccountTag key={account} account={account} />
        ))}
      </div>
    );
  }
}

export class HealthCountsCell extends React.Component<ICellRendererProps> {
  private cellContent(instanceCounts: IInstanceCounts) {
    switch (instanceCounts) {
      case undefined:
        return <Spinner size="small" />;
      case null:
        return <span>?</span>;
      default:
        return <HealthCounts container={instanceCounts} />;
    }
  }

  public render() {
    const { item, col } = this.props;
    return <div className={colClass(col.key)}>{this.cellContent(item[col.key])}</div>;
  }
}

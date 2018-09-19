import * as React from 'react';
import { startCase, kebabCase } from 'lodash';

import { AccountTag } from 'core/account';
import { Spinner } from 'core/widgets';
import { IInstanceCounts } from 'core/domain';
import { HealthCounts } from 'core/healthCounts';

export interface ISearchColumn {
  key: string;
  label?: string;
  notSortable?: boolean;
  scopeField?: boolean;
}

/****** Layout Renderers ******/

export class TableHeader extends React.Component {
  public render() {
    return <div className="table-header">{this.props.children}</div>;
  }
}

export class TableRow extends React.Component {
  public render() {
    return <div className="table-row small">{this.props.children}</div>;
  }
}

export class TableBody extends React.Component {
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

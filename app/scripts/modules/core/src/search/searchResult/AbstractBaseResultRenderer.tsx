import { get } from 'lodash';
import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { capitalize } from 'lodash';

import { NgReact } from 'core/reactShims';
import { IResultRenderer } from './searchResultFormatter.registry';

export interface ITableColumnConfigEntry<T> {
  key: string;
  label?: string;
  defaultValue?: string;
  notSortable?: boolean;
  scopeField?: boolean;
  cellRenderer: (item: T, key?: string, defaultValue?: string) => JSX.Element;
}

@BindAll()
export abstract class AbstractBaseResultRenderer<T> implements IResultRenderer {

  private gridElement: HTMLElement;

  public abstract getRendererClass(): string;

  public abstract getKey(item: T): string;

  public abstract getColumnConfig(): ITableColumnConfigEntry<T>[]

  public sortItems(items: T[]): T[] {
    return items;
  }

  public defaultCellRender(item: T, key: string): JSX.Element {
    return <div className={`${this.getRendererClass()}-${key}`} key={key}>{get(item, key)}</div>;
  };

  public hrefCellRenderer(item: T, key: string): JSX.Element {
    return (
      <div
        key={key}
        className={`${this.getRendererClass()}-${key}`}
      >
        <a href={get(item, 'href')}>{get(item, key)}</a>
      </div>
    );
  }

  public accountCellRenderer(item: T, key: string): JSX.Element {

    let result: JSX.Element;
    if (get(item, key)) {
      const { AccountTag } = NgReact;
      const accounts = get<string>(item, key).split(',').sort().map((account: string) => (
        <AccountTag key={account} account={account}/>));
      result = (
        <div className={`${this.getRendererClass()}-account`} key="env">
          {accounts}
        </div>
      );
    } else {
      result = (<div key="unknown" className={`${this.getRendererClass()}-account`}>-</div>);
    }

    return result;
  };

  public valueOrDefaultCellRenderer(item: T, key: string, defaultValue = ''): JSX.Element {
    return <div className={`${this.getRendererClass()}-${key}`} key={key}>{get(item, key) || defaultValue}</div>;
  }

  public scrollToTop(): void {
    this.gridElement.scrollTop = 0;
  }

  private refCallback(element: HTMLElement): void {
    if (element) {
      this.gridElement = element;
    }
  }

  private renderHeaderCell(key: string, label: string): JSX.Element {
    return (
      <div key={key} className={`${this.getRendererClass()}-header ${this.getRendererClass()}-${key}`}>
        {label}
      </div>
    );
  }

  private renderHeaderRow(): JSX.Element {

    const headerCells = this.getColumnConfig().map(c => this.renderHeaderCell(c.key, c.label || capitalize(c.key)));
    return (
      <div className="table-header">
        {headerCells}
      </div>
    );
  }

  private renderRow(item: T): JSX.Element {

    const rowClass = `${this.getRendererClass()}-row small`;
    const columns = this.getColumnConfig().map(c => c.cellRenderer(item, c.key, c.defaultValue));
    return (
      <div key={this.getKey(item)} className={rowClass}>
        {columns}
      </div>
    );
  }

  public render(items: T[]): JSX.Element {

    const rows = this.sortItems(items || []).map((item: T) => this.renderRow(item));
    return (
      <div ref={this.refCallback} className={`table ${this.getRendererClass()}-table`}>
        {this.renderHeaderRow()}
        <div className="table-contents flex-fill">
          {rows}
        </div>
      </div>
    );
  }
}

import * as React from 'react';
import * as classNames from 'classnames';

import { ITableColumn } from './tableColumn';
import { TableHeader } from './tableHeader';

export interface ITableProps<T> {
  rows: T[];
  columns: ITableColumn<T>[];
  rowKey: (row: T) => string;
  tableBodyClassName?: string;
  rowClassName?: (row: T) => string;
  onRowClick?: (row: T) => void;
}

export function Table<T>({ rows, columns, rowKey, tableBodyClassName, rowClassName, onRowClick }: ITableProps<T>) {
  return (
    <div>
      <TableHeader columns={columns} className="table-header"/>
      <ul className={tableBodyClassName || 'list-group'}>
        {
          rows.map(r => (
            <li
              key={rowKey(r)}
              onClick={onRowClick ? () => onRowClick(r) : null}
              className={classNames({ horizontal: !rowClassName, 'table-row': !rowClassName }, rowClassName && rowClassName(r))}
            >
              {
                columns.map((c, i) => (
                  <div key={c.label || i} className={`flex-${c.width}`}>
                    {!c.hide && c.getContent(r)}
                  </div>
                ))
              }
            </li>
          ))
        }
      </ul>
    </div>
  );
}


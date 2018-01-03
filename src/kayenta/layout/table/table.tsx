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
  customRow?: (row: T, columns?: ITableColumn<T>[]) => JSX.Element;
}

export function Table<T>({ rows, columns, rowKey, tableBodyClassName, rowClassName, onRowClick, customRow }: ITableProps<T>) {
  const TableRow = ({ row }: { row: T }) => (
    <li
      key={rowKey(row)}
      onClick={onRowClick ? () => onRowClick(row) : null}
      className={classNames({ horizontal: !rowClassName, 'table-row': !rowClassName }, rowClassName && rowClassName(row))}
    >
      {
        columns.map((c, i) => (
          <div key={c.label || i} className={`flex-${c.width}`}>
            {!c.hide && c.getContent(row)}
          </div>
        ))
      }
    </li>
  );

  return (
    <div>
      <TableHeader columns={columns} className="table-header"/>
      <ul className={tableBodyClassName || 'list-group'}>
        {
          rows.map(r => (
            customRow && customRow(r, columns)
              ? customRow(r, columns)
              : <TableRow row={r}/>
          ))
        }
      </ul>
    </div>
  );
}


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
  customRow?: (row: T, tableProps: ITableProps<T>) => JSX.Element;
  className?: string;
}

export function Table<T>(props: ITableProps<T>) {
  const { rows, columns, rowKey, tableBodyClassName, rowClassName, onRowClick, customRow, className } = props;
  const TableRow = ({ row }: { row: T }) => (
    <li
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
    <div className={className}>
      <TableHeader columns={columns} className="table-header"/>
      <ul className={classNames(tableBodyClassName, 'list-group')}>
        {
          rows.map(r => (
            customRow && customRow(r, props)
              ? <div key={rowKey(r)}>{customRow(r, props)}</div>
              : <TableRow key={rowKey(r)} row={r}/>
          ))
        }
      </ul>
    </div>
  );
}


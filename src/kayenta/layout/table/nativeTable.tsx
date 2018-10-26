import * as React from 'react';
import * as classNames from 'classnames';

import { ITableColumn } from './tableColumn';
import { NativeTableHeader } from './nativeTableHeader';

export interface INativeTableProps<T> {
  rows: T[];
  columns: ITableColumn<T>[];
  rowKey: (row: T) => string;
  tableBodyClassName?: string;
  headerClassName?: string;
  rowClassName?: (row: T) => string;
  onRowClick?: (row: T) => void;
  customRow?: (row: T) => JSX.Element;
  className?: string;
}

export function NativeTable<T>({
  rows,
  columns,
  rowKey,
  tableBodyClassName,
  rowClassName,
  onRowClick,
  customRow,
  className,
  headerClassName,
}: INativeTableProps<T>) {
  const TableRow = ({ row }: { row: T }) => (
    <tr
      onClick={onRowClick ? () => onRowClick(row) : null}
      className={classNames({ 'table-row': !rowClassName }, rowClassName && rowClassName(row))}
    >
      {columns.map(({ label, hide, getContent }, i) => (
        <td key={label || i}>{(!hide || !hide(row)) && getContent(row)}</td>
      ))}
    </tr>
  );

  return (
    <table className={className}>
      <NativeTableHeader rows={rows} columns={columns} className={classNames('table-header', headerClassName)} />
      <tbody className={tableBodyClassName}>
        {rows.map(
          r =>
            customRow && customRow(r) ? <td key={rowKey(r)}>{customRow(r)}</td> : <TableRow key={rowKey(r)} row={r} />,
        )}
      </tbody>
    </table>
  );
}

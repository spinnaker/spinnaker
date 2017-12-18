import * as React from 'react';

import { ITableColumn } from './tableColumn';
import { TableHeader } from './tableHeader';

export interface ITableProps<T> {
  rows: T[];
  columns: ITableColumn<T>[];
  rowKey: (row: T) => string;
}

export function Table<T>({ rows, columns, rowKey }: ITableProps<T>) {
  return (
    <div>
      <TableHeader columns={columns} className="table-header"/>
      <ul className="list-group">
        {
          rows.map(r => (
            <div key={rowKey(r)} className="horizontal table-row">
              {
                columns.map((c, i) => (
                  <div key={c.label || i} className={`flex-${c.width}`}>
                    {!c.hide && c.getContent(r)}
                  </div>
                ))
              }
            </div>
          ))
        }
      </ul>
    </div>
  );
}


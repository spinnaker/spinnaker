import * as React from 'react';
import * as classNames from 'classnames';

import { ITableColumn } from './tableColumn';

export interface INativeTableHeaderProps {
  columns: ITableColumn<any>[];
  className: string;
}

export const NativeTableHeader = ({ columns, className }: INativeTableHeaderProps) => {
  return (
    <thead className={className}>
      <tr>
        {columns.map(({ label, labelClassName, hide }, i) => (
          <th key={label || i} className="native-table-header">
            {!hide && (
              <h6 className={classNames('heading-6', 'uppercase', 'color-text-primary', labelClassName)}>{label}</h6>
            )}
          </th>
        ))}
      </tr>
    </thead>
  );
};

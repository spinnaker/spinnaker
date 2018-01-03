import * as React from 'react';
import * as classNames from 'classnames';

import { ITableColumn } from './tableColumn';

export interface ITableHeaderProps {
  columns: ITableColumn<any>[];
  className: string;
}

export const TableHeader = ({ columns, className }: ITableHeaderProps) => {
  return (
    <section className={classNames('horizontal', className)}>
      {columns.map((c, i) => (
        <div key={c.label || i} className={`flex-${c.width}`}>
          {!c.hide && (
            <h6 className={classNames('heading-6', 'uppercase', 'color-text-primary', c.labelClassName)}>
              {c.label}
            </h6>
          )}
        </div>
      ))}
    </section>
  );
};

import * as React from 'react';

import type { ITableLayoutProps } from './Table';
import type { ITableCellLayoutProps } from './TableCell';
import type { ITableRowLayoutProps } from './TableRow';

import './minimalNativeTableLayout.less';

const GUTTER_SIZE_PX = 16;
const INDENT_SIZE_PX = 20;

const TableLayout = ({ columns, children }: ITableLayoutProps) => {
  return (
    <table className="MinimalNativeTableLayout" cellSpacing="0" cellPadding="0">
      <thead>
        <tr className="header-row">
          {columns.map(({ name }) => (
            <th key={name} className="header-cell">
              {name}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>{children}</tbody>
    </table>
  );
};

const TableRowLayout = ({ children }: ITableRowLayoutProps) => {
  return <tr>{children}</tr>;
};

const TableCellLayout = ({ children, indent }: ITableCellLayoutProps) => {
  return (
    <td
      className="cell"
      style={{ paddingLeft: !!indent ? `${GUTTER_SIZE_PX + indent * INDENT_SIZE_PX}px` : undefined }}
    >
      {children}
    </td>
  );
};

export const minimalNativeTableLayout = {
  TableLayout,
  TableRowLayout,
  TableCellLayout,
};

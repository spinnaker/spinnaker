import * as React from 'react';

import { ITableCellLayoutProps } from './TableCell';
import { TableContext } from './TableContext';
import { ITableRowLayoutProps } from './TableRow';
import { useDeepObjectDiff, useIsMobile } from '../hooks';

const { useState, useMemo } = React;

export interface ITableColumn {
  name: string;
  sortDirection?: 'ascending' | 'descending';
  onSort?: () => any;
}

export type ITableColumns = Readonly<Array<string | ITableColumn>>;

export interface ITableLayoutProps {
  columns: ITableColumn[];
  isMobile: boolean;
  expandable?: boolean;
  expandAll: () => any;
  children?: React.ReactNode;
}

export interface ITableLayout {
  TableLayout: React.ComponentType<ITableLayoutProps>;
  TableRowLayout: React.ComponentType<ITableRowLayoutProps>;
  TableCellLayout: React.ComponentType<ITableCellLayoutProps>;
}

export interface ITableProps {
  layout: ITableLayout;
  columns: ITableColumns;
  expandable?: boolean;
  children?: React.ReactNode;
}

export const Table = (props: ITableProps) => {
  const isMobile = useIsMobile();
  const [allExpanded, setAllExpanded] = useState(false);

  const { layout, ...propsWithoutLayout } = props;

  const normalizedColumns = props.columns.map((column) => (typeof column === 'string' ? { name: column } : column));
  const tableContext = useMemo(
    () => ({
      layout,
      isMobile,
      allExpanded,
      setAllExpanded: () => setAllExpanded(false),
      expandable: props.expandable,
      columns: normalizedColumns,
    }),
    [layout, isMobile, props.expandable, allExpanded, useDeepObjectDiff(props.columns)],
  );

  const { TableLayout } = layout;

  return (
    <TableContext.Provider value={tableContext}>
      <TableLayout
        {...propsWithoutLayout}
        isMobile={isMobile}
        columns={normalizedColumns}
        expandAll={() => setAllExpanded(true)}
      />
    </TableContext.Provider>
  );
};

import * as React from 'react';

import { ITableContext, TableContext } from './TableContext';

const { useContext } = React;

export interface ITableCellProps {
  indent?: number;
  children?: React.ReactNode;
}

export interface ITableCellPropsWithInternalFields extends ITableCellProps {
  index: number;
}

export type ITableCellLayoutProps = ITableCellPropsWithInternalFields &
  Pick<ITableContext, 'expandable' | 'isMobile' | 'columns'>;

export const TableCell = (props: ITableCellProps) => {
  const { layout, expandable, isMobile, columns } = useContext(TableContext);

  const { TableCellLayout } = layout;

  return (
    <TableCellLayout
      // While the public TableCell API is ITableCellProps,
      // in reality the parent TableRow will hydrate things
      // and pass us ITableCellPropsWithInternalFields
      {...(props as ITableCellPropsWithInternalFields)}
      expandable={expandable}
      isMobile={isMobile}
      columns={columns}
    />
  );
};

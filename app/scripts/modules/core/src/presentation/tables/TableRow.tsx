import * as React from 'react';

import { ITableCellPropsWithInternalFields } from './TableCell';
import { ITableContext, TableContext } from './TableContext';

const { useState, useContext, useEffect } = React;

export interface ITableRowProps {
  defaultExpanded?: boolean;
  renderExpandedContent?: () => React.ReactNode;
  children?: React.ReactNode;
}
export type ITableRowLayoutProps = ITableRowProps &
  Pick<ITableContext, 'isMobile' | 'columns'> &
  Pick<ITableRowProps, 'renderExpandedContent'> & {
    tableExpandable: boolean;
    rowExpandable: boolean;
    expanded: boolean;
    setExpanded: (expanded: boolean) => any;
  };

const logDefaultExpandedError = () => {
  console.error(
    new Error(
      `You used the defaultExpanded prop on a row inside a <Table/> that's not expandable.
       Either remove the defaultExpanded prop or make the <Table/> expandable.`,
    ),
  );
};

const throwCellColumnError = () => {
  throw new Error(
    `A <TableCell/> was added that didn't match any of the columns defined on its parent <Table/>.
     Check if there are an equal number of <TableCell/> elements to the number of columns provided to the <Table/>.
    `,
  );
};

export const TableRow = ({ defaultExpanded, renderExpandedContent, children }: ITableRowProps) => {
  const { layout, expandable, allExpanded, setAllExpanded, isMobile, columns } = useContext(TableContext);
  const [expanded, setExpanded] = useState(defaultExpanded || false);

  // Expand individual rows when the table expands all its rows at once
  useEffect(() => {
    allExpanded && renderExpandedContent && setExpanded(true);
  }, [allExpanded]);

  if (!expandable && defaultExpanded != null) {
    logDefaultExpandedError();
  }

  const childrenWithIndex = React.Children.map(children, (cell, index) => {
    return React.cloneElement(cell as React.ReactElement<ITableCellPropsWithInternalFields>, { index });
  });

  if (childrenWithIndex.length > columns.length) {
    throwCellColumnError();
  }

  const { TableRowLayout } = layout;

  return (
    <TableRowLayout
      tableExpandable={expandable}
      rowExpandable={expandable && !!renderExpandedContent}
      expanded={expanded}
      setExpanded={(expand) => {
        setExpanded(expand);
        // When all rows were previously expanded, clear away that state
        // the first time an individual row is collapsed
        if (allExpanded && !expand) {
          setAllExpanded(false);
        }
      }}
      renderExpandedContent={renderExpandedContent}
      isMobile={isMobile}
      columns={columns}
      children={childrenWithIndex}
    />
  );
};

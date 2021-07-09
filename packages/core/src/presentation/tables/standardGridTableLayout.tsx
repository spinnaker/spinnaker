import classNames from 'classnames';
import * as React from 'react';

import { ITableLayoutProps } from './Table';
import { ITableCellLayoutProps } from './TableCell';
import { ITableRowLayoutProps } from './TableRow';

import './standardGridTableLayout.less';

export interface ITableColumnSize {
  size: number;
  unit: 'fr' | '%' | 'px' | 'em' | 'rem' | 'vh' | 'vw' | 'vmin' | 'vmax';
}

export type ITableColumnSizes = Readonly<Array<number | ITableColumnSize | [ITableColumnSize, ITableColumnSize]>>;

// If the table rows are expandable, we'll need a column on the right
// for the expand/collapse indicators
const EXPAND_COLLAPSE_COLUMN_SIZE = '48px';

const GUTTER_SIZE_PX = 16;
const INDENT_SIZE_PX = 20;

const throwSizingError = (invalidSizing: any) => {
  throw new Error(
    `Invalid table sizing configuration.
     Expected either a sizing object with 'size' and 'unit' fields or an array of two sizing objects.
     Actual value was ${JSON.stringify(invalidSizing)}`,
  );
};

export const getGridColumnsStyle = (sizes: ITableColumnSizes, expandable: boolean, isMobile: boolean) => {
  if (isMobile) {
    return undefined;
  }

  const gridColumns = sizes.map((sizing) => {
    if (typeof sizing === 'number') {
      return `${sizing}fr`;
    } else if (Array.isArray(sizing) && sizing.length === 2) {
      const [min, max] = sizing;
      return `minmax(${min.size + min.unit}, ${max.size + max.unit})`;
    } else if (!Array.isArray(sizing) && sizing.size && sizing.unit) {
      return `${sizing.size}${sizing.unit}`;
    } else {
      return throwSizingError(sizing);
    }
  });

  if (expandable) {
    gridColumns.push(EXPAND_COLLAPSE_COLUMN_SIZE);
  }

  return gridColumns.join(' ');
};

const TableLayout = ({
  columns,
  expandable,
  expandAll,
  isMobile,
  children,
  sizes,
}: ITableLayoutProps & { sizes: ITableColumnSizes }) => {
  const gridTemplateColumns = getGridColumnsStyle(sizes, expandable, isMobile);

  return (
    <div className="StandardGridTableLayout">
      {!isMobile && (
        <div className="standard-grid-table-header" style={{ gridTemplateColumns }}>
          {columns.map(({ name, sortDirection, onSort }) => (
            <div
              key={name}
              className={classNames('standard-grid-table-cell', { sortable: !!onSort })}
              onClick={onSort || null}
            >
              {name}
              {sortDirection ? <span className={classNames('sort-indicator', sortDirection)} /> : null}
            </div>
          ))}
          {expandable && (
            <div className="standard-grid-table-cell header-expand-toggle" onClick={expandAll}>
              <i className="ico icon-expand-all" />
            </div>
          )}
        </div>
      )}
      {children}
    </div>
  );
};

const TableRowLayout = ({
  tableExpandable,
  rowExpandable,
  expanded,
  setExpanded,
  isMobile,
  sizes,
  children,
  renderExpandedContent,
}: ITableRowLayoutProps & { sizes: ITableColumnSizes }) => {
  const gridTemplateColumns = getGridColumnsStyle(sizes, tableExpandable, isMobile);

  return (
    <div
      className={classNames('standard-grid-table-row-container', {
        'sp-margin-l-bottom': rowExpandable && expanded,
      })}
    >
      <div
        className={classNames('standard-grid-table-row', { expandable: rowExpandable, expanded })}
        style={{ gridTemplateColumns }}
        onClick={rowExpandable ? () => setExpanded(!expanded) : null}
      >
        {children}
        {tableExpandable && !isMobile && (
          <div className="standard-grid-table-cell row-expand-toggle">
            {rowExpandable && (
              <i className={classNames('ico', { 'icon-expand': !expanded, 'icon-collapse': expanded })} />
            )}
          </div>
        )}
      </div>
      {rowExpandable && expanded && <div className="expanded-row-content">{renderExpandedContent()}</div>}
    </div>
  );
};

const TableCellLayout = ({ children, indent, index, columns, isMobile }: ITableCellLayoutProps) => {
  const columnName = columns[index].name;
  const indentPx = !!indent && isMobile ? `${GUTTER_SIZE_PX + indent * INDENT_SIZE_PX}px` : undefined;

  return (
    <div className="standard-grid-table-cell" style={{ paddingLeft: indentPx }}>
      {isMobile && <div className="mobile-cell-header">{columnName}</div>}
      <div className="cell-content">{children}</div>
    </div>
  );
};

export const standardGridTableLayout = (sizes: ITableColumnSizes) => ({
  TableLayout: (props: ITableLayoutProps) => <TableLayout {...props} sizes={sizes} />,
  TableRowLayout: (props: ITableRowLayoutProps) => <TableRowLayout {...props} sizes={sizes} />,
  TableCellLayout: (props: ITableCellLayoutProps) => <TableCellLayout {...props} />,
});

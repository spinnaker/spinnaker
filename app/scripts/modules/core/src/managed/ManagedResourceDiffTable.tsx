import React from 'react';

import { Table, TableRow, TableCell, minimalNativeTableLayout, BreakString, SingleLineString } from 'core/presentation';
import { IManagedResourceDiff } from 'core/domain';

const { memo } = React;

export interface IManagedResourceDiffTableProps {
  diff: IManagedResourceDiff;
}

interface DiffRow {
  key: string;
  name: string;
  depth: number;
  isLeafNode: boolean;
  type: 'CHANGED' | 'ADDED' | 'REMOVED';
  actual?: string;
  desired?: string;
}

const traverseDiffFields = (diff: IManagedResourceDiff, depth: number, rows: DiffRow[]) => {
  Object.keys(diff)
    .sort((a, b) => a.localeCompare(b))
    .forEach((name) => {
      const { key, diffType, actual, desired, fields } = diff[name];
      rows.push({
        key,
        name,
        depth,
        actual,
        desired,
        type: diffType,
        isLeafNode: !fields,
      });

      if (fields) {
        traverseDiffFields(fields, depth + 1, rows);
      }
    });

  return rows;
};

const tableRowsFromDiff = (diff: IManagedResourceDiff): DiffRow[] => traverseDiffFields(diff, 0, []);

export const ManagedResourceDiffTable = memo(({ diff }: IManagedResourceDiffTableProps) => {
  const diffRows = tableRowsFromDiff(diff);

  return (
    <Table layout={minimalNativeTableLayout} columns={['configuration', 'change', 'actual', 'desired']}>
      {diffRows.map(({ key, name, depth, isLeafNode, type, actual, desired }) => (
        <TableRow key={key}>
          <TableCell indent={depth}>
            <SingleLineString>{name}</SingleLineString>
          </TableCell>
          <TableCell>
            {isLeafNode && <SingleLineString className="text-italic">{type.toLowerCase()}</SingleLineString>}
          </TableCell>
          <TableCell>
            <BreakString>{actual}</BreakString>
          </TableCell>
          <TableCell>
            <BreakString>{desired}</BreakString>
          </TableCell>
        </TableRow>
      ))}
    </Table>
  );
});

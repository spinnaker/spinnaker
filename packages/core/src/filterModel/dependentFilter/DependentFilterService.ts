import { chain, identity, pickBy } from 'lodash';

import { ISortFilter } from '../IFilterModel';

function generateIterator(sortFilter: ISortFilter & { [key: string]: any }) {
  return function iterator(acc: { headings: any; pool: any }, headingType: string) {
    const { headings, pool } = acc;
    headings[headingType] = chain(pool).map(headingType).uniq().compact().value().sort();
    unselectUnavailableHeadings(headings[headingType], sortFilter[headingType]);
    acc.pool = filterPoolBySelectedHeadings(pool, headingType, sortFilter);
    return acc;
  };
}

function filterPoolBySelectedHeadings(
  pool: any,
  headingType: string,
  sortFilter: ISortFilter & { [key: string]: any },
) {
  const selectedHeadings = sortFilter[headingType];
  if (!Object.keys(pickBy(selectedHeadings, identity)).length) {
    return pool;
  }

  return pool.filter((unit: any) => selectedHeadings[unit[headingType]]);
}

function unselectUnavailableHeadings(headings: string[], selectedHeadings: { [key: string]: {} }) {
  if (!selectedHeadings) {
    return;
  }

  const headingSet = new Set(headings);
  Object.keys(selectedHeadings).forEach((key) => {
    if (!headingSet.has(key)) {
      delete selectedHeadings[key];
    }
  });
}

export function digestDependentFilters({
  pool,
  dependencyOrder,
  sortFilter,
}: {
  pool: {};
  dependencyOrder: string[];
  sortFilter: ISortFilter;
}) {
  return dependencyOrder.reduce(generateIterator(sortFilter), { pool, headings: {} }).headings;
}

import React from 'react';

import { CollapsibleSectionStateCache } from '../cache';
import { logger } from '../utils';

export interface IFilter {
  label: string;
  value: string;
}

export interface IFilterTag extends IFilter {
  clear: () => any;
  key: string;
}

export interface IFilterTagsProps {
  tags: IFilterTag[];
  tagCleared?: () => any;
  clearFilters: () => any;
}

export interface IFilterTagsState {
  tags: IFilterTag[];
}

export const FilterTags = ({ clearFilters, tags, tagCleared }: IFilterTagsProps) => {
  const hasExtraTags = tags?.length > 5;
  const [showExtraTags, setShowExtraTags] = React.useState(
    hasExtraTags && CollapsibleSectionStateCache.isExpanded('filterTags'),
  );
  const visibleTags = !hasExtraTags || showExtraTags ? tags : tags.slice(0, 5);

  const toggleExtraTags = () => {
    CollapsibleSectionStateCache.setExpanded('filterTags', !showExtraTags);
    setShowExtraTags(!showExtraTags);
  };

  const clearAllFilters = (): void => {
    if (clearFilters) {
      clearFilters();
    }
    logger.log({ category: 'Filter Tags', action: 'Clear All clicked' });
  };

  const clearIndividualFilter = (tag: IFilterTag): void => {
    tag.clear();
    tagCleared();
    logger.log({ category: 'Filter Tags', action: 'Individual tag removed' });
  };

  return (
    <div className="col-md-12 filter-tags">
      {visibleTags && visibleTags.length > 0 && (
        <span>
          <span>Filtered by: </span>
          {visibleTags.map((tag) => (
            <span className="filter-tag" key={[tag.label, tag.value].join(':')}>
              <strong>{tag.label}</strong>: {tag.value}
              <a className="clickable clear-filters" onClick={() => clearIndividualFilter(tag)}>
                <span className="glyphicon glyphicon-remove-sign" />
              </a>
            </span>
          ))}
          {hasExtraTags && (
            <span className="filter-toggle" onClick={toggleExtraTags}>
              {showExtraTags ? 'Hide List' : `See all ${tags.length} current filters`}
            </span>
          )}
          {visibleTags.length > 1 && (
            <a className="clickable clear-filters" onClick={clearAllFilters}>
              Clear All
            </a>
          )}
        </span>
      )}
    </div>
  );
};

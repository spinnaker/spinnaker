import React from 'react';

import { SearchResultPodItem } from './SearchResultPodItem';
import { ISearchResultPodData } from './SearchResultPods';
import { robotToHuman } from '../../presentation';

export interface ISearchResultPodProps {
  podData: ISearchResultPodData;
  onResultClick: (category: string) => void;
  onRemoveItem: (category: string, itemId: string) => void;
}

export class SearchResultPod extends React.Component<ISearchResultPodProps> {
  public render() {
    const { podData } = this.props;
    const { category } = podData;

    return (
      <div className="col-md-4 category-container" key={category}>
        <div className="panel category row">
          <div className="summary">
            <span className="category-icon">
              {podData.config.iconClass && <span className={podData.config.iconClass} />}
            </span>
            {robotToHuman(category)} ({podData.results.length})
          </div>

          <div className="details-container list-group">
            {podData.results.map((result) => (
              <SearchResultPodItem
                key={result.id}
                categoryName={category}
                result={result}
                onResultClick={this.props.onResultClick}
                onRemoveItem={this.props.onRemoveItem}
              />
            ))}
          </div>
        </div>
      </div>
    );
  }
}

import * as React from 'react';

import { robotToHuman } from 'core/presentation';

import { SearchResultPodItem } from './SearchResultPodItem';
import { ISearchResultPodData } from './SearchResultPods';

export interface ISearchResultPodProps {
  podData: ISearchResultPodData;
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
              {podData.config.iconClass && <span className={podData.config.iconClass}/>}
            </span>
            {robotToHuman(category)} ({podData.results.length})
          </div>

          <div className="details-container list-group">
            {podData.results.map(result => (
              <SearchResultPodItem
                key={result.id}
                categoryName={category}
                result={result}
                onRemoveItem={this.props.onRemoveItem}
              />
            ))}
          </div>
        </div>
      </div>
    );
  }
}

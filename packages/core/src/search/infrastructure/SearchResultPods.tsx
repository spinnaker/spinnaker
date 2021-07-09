import { get } from 'lodash';
import React from 'react';

import { ProjectSummaryPod } from './ProjectSummaryPod';
import { SearchResultPod } from './SearchResultPod';
import { IRecentHistoryEntry } from '../../history';
import { SearchResultType } from '../searchResult/searchResultType';

export type ISearchResult = IRecentHistoryEntry & { displayName: string; account?: string };

export interface ISearchResultPodData {
  category: string;
  config: SearchResultType;
  results: ISearchResult[];
}

export interface ISearchResultPodsProps {
  results: ISearchResultPodData[];
  onRemoveProject?: (projectId: string) => void;
  onRemoveItem?: (categoryName: string, itemId: string) => void;
  onResultClick: (categoryName: string) => void;
}

export class SearchResultPods extends React.Component<ISearchResultPodsProps> {
  public render() {
    const { results } = this.props;

    if (!results.length) {
      return null;
    }

    const projects: ISearchResultPodData = results.find((x) => x.category === 'projects');
    const otherCategories: ISearchResultPodData[] = results
      .filter((x) => x.category !== 'projects')
      .sort((a, b) => a.category.localeCompare(b.category));

    return (
      <div className="infrastructure-section container">
        <div className="recent-items">
          <h3>Recently viewed</h3>

          <div className="row">
            {projects && (
              <div className="col-md-3">
                <div className="row">
                  <div className="col-md-12">
                    {projects.results.map((project) => (
                      <ProjectSummaryPod
                        key={project.id}
                        id={project.id}
                        projectName={get(project, 'params.project')}
                        applications={get(project, 'extraData.config.applications', [])}
                        onRemoveProject={this.props.onRemoveProject}
                        onResultClick={this.props.onResultClick}
                      />
                    ))}
                  </div>
                </div>
              </div>
            )}

            <div className={`col-md-${projects ? 9 : 12}`}>
              <div className="row">
                {otherCategories.map((category) => (
                  <SearchResultPod
                    key={category.category}
                    podData={category}
                    onRemoveItem={this.props.onRemoveItem}
                    onResultClick={() => this.props.onResultClick(category.category)}
                  />
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }
}

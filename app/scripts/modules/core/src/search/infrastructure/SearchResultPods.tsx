import * as React from 'react';
import { get } from 'lodash';

import { IRecentHistoryEntry } from 'core/history';
import { ISearchResultType } from 'core/search';

import { SearchResultPod } from './SearchResultPod';
import { ProjectSummaryPod } from './ProjectSummaryPod';

export type ISearchResult = IRecentHistoryEntry & { displayName: string };

export interface ISearchResultPodData {
  category: string;
  config: ISearchResultType;
  results: ISearchResult[];
}

export interface ISearchResultPodsProps {
  results: ISearchResultPodData[];
  onRemoveProject?: (projectId: string) => void;
  onRemoveItem?: (categoryName: string, itemId: string) => void;
}

export class SearchResultPods extends React.Component<ISearchResultPodsProps> {
  public render() {
    const { results } = this.props;
    const projects: ISearchResultPodData = results.find(x => x.category === 'projects');
    const otherCategories: ISearchResultPodData[] = results.filter(x => x.category !== 'projects')
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
                    {projects.results.map(project => (
                      <ProjectSummaryPod
                        key={project.id}
                        id={project.id}
                        projectName={get(project, 'params.project')}
                        applications={get(project, 'extraData.config.applications', [])}
                        onRemoveProject={this.props.onRemoveProject}
                      />
                    ))}
                  </div>
                </div>
              </div>
            )}

            <div className={`col-md-${projects ? 9 : 12}`}>
              <div className="row">
                {otherCategories.map(category => (
                  <SearchResultPod
                    key={category.category}
                    podData={category}
                    onRemoveItem={this.props.onRemoveItem}
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

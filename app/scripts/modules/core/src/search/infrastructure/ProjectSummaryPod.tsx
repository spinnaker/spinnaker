import { UISref } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react-hybrid';
import React from 'react';

import { Tooltip } from '../../presentation';

import './projectSummaryPod.less';

export interface IProjectSummaryPodProps {
  id: string;
  projectName: string;
  applications: string[];
  onRemoveProject?: (projectName: string) => void;
  onResultClick: (categoryName: string) => void;
}

@UIRouterContext
export class ProjectSummaryPod extends React.Component<IProjectSummaryPodProps> {
  private handleRemoveClicked = (evt: React.MouseEvent<any>) => {
    evt.preventDefault();
    this.props.onRemoveProject(this.props.id);
  };

  private handleResultClick = (): void => {
    if (this.props.onResultClick) {
      this.props.onResultClick('projects');
    }
  };

  public render() {
    const { projectName, applications, onRemoveProject } = this.props;
    const showRemoveButton = !!onRemoveProject;

    return (
      <div className="infrastructure-project" onClick={this.handleResultClick}>
        <div className="project-summary">
          <div className="project-name">
            <UISref to="home.project.dashboard" params={{ project: projectName }}>
              <a>{projectName}</a>
            </UISref>

            {showRemoveButton && (
              <span className="small clickable remove-result-link" onClick={this.handleRemoveClicked}>
                <Tooltip value="remove from history" placement="left" delayShow={300}>
                  <span className="glyphicon glyphicon-remove" />
                </Tooltip>
              </span>
            )}
          </div>
          <div className="application-count">
            {applications.length || 0} Application
            {applications.length !== 1 ? 's' : ''}
          </div>
        </div>

        <ul className="application-list">
          {applications.map((application) => (
            <li key={application}>
              <UISref to="home.project.application.insight.clusters" params={{ project: projectName, application }}>
                <a>{application}</a>
              </UISref>
            </li>
          ))}
        </ul>
      </div>
    );
  }
}

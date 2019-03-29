import * as React from 'react';
import { Modal } from 'react-bootstrap';
import { DateTime } from 'luxon';
import { get, memoize } from 'lodash';
import { Subscription } from 'rxjs';
import { UISref } from '@uirouter/react';
import { PipelineTemplateV2Service } from './pipelineTemplateV2.service';
import { IPipelineTemplateV2 } from 'core/domain/IPipelineTemplateV2';
import { ShowPipelineTemplateJsonModal } from 'core/pipeline/config/actions/templateJson/ShowPipelineTemplateJsonModal';
import { ReactInjector, IStateChange } from 'core/reactShims';
import { PipelineTemplateReader } from '../PipelineTemplateReader';

import './PipelineTemplatesV2.less';

export interface IPipelineTemplatesV2State {
  templates: IPipelineTemplateV2[];
  fetchError: string;
  searchValue: string;
  templateId: string;
}

export const PipelineTemplatesV2Error = (props: { message: string }) => {
  return (
    <div className="pipeline-templates-error-banner horizontal middle center heading-4">
      <i className="fa fa-exclamation-triangle" />
      <span>{props.message}</span>
    </div>
  );
};

export class PipelineTemplatesV2 extends React.Component<{}, IPipelineTemplatesV2State> {
  private routeChangedSubscription: Subscription = null;

  constructor(props: {}) {
    super(props);
    const { $stateParams } = ReactInjector;
    const templateId: string = $stateParams.templateId;
    this.state = { templates: [], fetchError: null, searchValue: '', templateId };
  }

  public componentDidMount() {
    PipelineTemplateReader.getV2PipelineTemplateList().then(
      templates => this.setState({ templates }),
      err => {
        if (err) {
          this.setState({ fetchError: get(err, 'data.message') || get(err, 'message') || String(err) });
        } else {
          this.setState({ fetchError: 'An unknown error occurred while fetching templates' });
        }
      },
    );
    this.routeChangedSubscription = ReactInjector.stateEvents.stateChangeSuccess.subscribe(this.onRouteChanged);
  }

  public componentWillUnmount() {
    this.routeChangedSubscription.unsubscribe();
  }

  private onRouteChanged = (stateChange: IStateChange) => {
    const { to, toParams } = stateChange;
    if (to.name === 'home.pipeline-templates') {
      this.setState({ templateId: null });
    } else if (to.name === 'home.pipeline-templates.pipeline-templates-detail') {
      const { templateId } = toParams as { templateId?: string };
      this.setState({ templateId: templateId || null });
    }
  };

  private sortTemplates = (templates: IPipelineTemplateV2[]) => {
    return templates.slice().sort((a: IPipelineTemplateV2, b: IPipelineTemplateV2) => {
      const aEpoch = Number.parseInt(a.updateTs, 10);
      const bEpoch = Number.parseInt(b.updateTs, 10);
      // Most recent templates appear at top of list
      if (isNaN(aEpoch)) {
        return 1;
      } else if (isNaN(bEpoch)) {
        return -1;
      } else {
        return bEpoch - aEpoch;
      }
    });
  };

  private getUpdateTimeForTemplate = (template: IPipelineTemplateV2) => {
    const millis = Number.parseInt(template.updateTs, 10);
    if (isNaN(millis)) {
      return '';
    }
    const dt = DateTime.fromMillis(millis);
    return dt.toLocaleString(DateTime.DATETIME_SHORT);
  };

  private dismissDetailsModal = () => {
    ReactInjector.$state.go('home.pipeline-templates');
  };

  private onSearchFieldChanged = (event: React.SyntheticEvent<HTMLInputElement>) => {
    const searchValue: string = get(event, 'target.value', '');
    this.setState({ searchValue });
  };

  // Creates a cache key suitable for _.memoize
  private filterMemoizeResolver = (templates: IPipelineTemplateV2[], query: string): string => {
    return templates.reduce((s, t) => s + PipelineTemplateV2Service.idForTemplate(t), '') + query;
  };

  private filterSearchResults = memoize((templates: IPipelineTemplateV2[], query: string): IPipelineTemplateV2[] => {
    const searchValue = query.trim().toLowerCase();
    if (!searchValue) {
      return templates;
    } else {
      return templates.filter(template => {
        const name = get(template, 'metadata.name', '').toLowerCase();
        const description = get(template, 'metadata.description', '').toLowerCase();
        const owner = get(template, 'metadata.owner', '').toLowerCase();
        return name.includes(searchValue) || description.includes(searchValue) || owner.includes(searchValue);
      });
    }
  }, this.filterMemoizeResolver);

  private getViewedTemplate() {
    return this.state.templates.find(t => PipelineTemplateV2Service.idForTemplate(t) === this.state.templateId);
  }

  public render() {
    const { templates, searchValue, fetchError, templateId } = this.state;
    const detailsTemplate = templateId ? this.getViewedTemplate() : null;
    const searchPerformed = searchValue.trim() !== '';
    const filteredResults = this.sortTemplates(this.filterSearchResults(templates, searchValue));
    const resultsAvailable = filteredResults.length > 0;
    return (
      <>
        <div className="infrastructure">
          <div className="infrastructure-section search-header">
            <div className="container">
              <h2 className="header-section">
                <span className="search-label">Pipeline Templates</span>
                <input
                  type="search"
                  placeholder="Search pipeline templates"
                  className="form-control input-md"
                  ref={input => input && input.focus()}
                  onChange={this.onSearchFieldChanged}
                  value={searchValue}
                />
              </h2>
            </div>
          </div>
          <div className="infrastructure-section">
            {fetchError && (
              <div className="container">
                <PipelineTemplatesV2Error message={`There was an error fetching pipeline templates: ${fetchError}`} />
              </div>
            )}
            {searchPerformed && !resultsAvailable && (
              <div className="container">
                <h4>No matches found for '{searchValue}'</h4>
              </div>
            )}
            {resultsAvailable && (
              <div className="container">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Name</th>
                      <th>Owner</th>
                      <th>Updated</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredResults.map(template => {
                      const { metadata } = template;
                      const templateId = PipelineTemplateV2Service.idForTemplate(template);
                      return (
                        <tr key={templateId}>
                          <td>{metadata.name || '-'}</td>
                          <td>{metadata.owner || '-'}</td>
                          <td>{this.getUpdateTimeForTemplate(template) || '-'}</td>
                          <td className="pipeline-template-actions">
                            <UISref to={`.pipeline-templates-detail`} params={{ templateId }}>
                              <button className="link" onClick={() => this.setState({ templateId })}>
                                View
                              </button>
                            </UISref>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
        {detailsTemplate && (
          <Modal show={true} dialogClassName="modal-lg modal-fullscreen" onHide={() => {}}>
            <ShowPipelineTemplateJsonModal
              template={detailsTemplate}
              editable={false}
              modalHeading="View PipelineTemplate"
              descriptionText="The JSON below contains the metadata, variables and pipeline definition for this template."
              dismissModal={this.dismissDetailsModal}
            />
          </Modal>
        )}
      </>
    );
  }
}

import * as React from 'react';
import { Modal } from 'react-bootstrap';
import { DateTime } from 'luxon';
import { get, memoize } from 'lodash';
import { Subscription } from 'rxjs';
import { UISref } from '@uirouter/react';

import { PipelineTemplateV2Service } from './pipelineTemplateV2.service';
import { CreatePipelineFromTemplate } from './createPipelineFromTemplate';
import { IPipelineTemplateV2 } from 'core/domain/IPipelineTemplateV2';
import { ShowPipelineTemplateJsonModal } from 'core/pipeline/config/actions/templateJson/ShowPipelineTemplateJsonModal';
import { ReactInjector, IStateChange } from 'core/reactShims';
import { PipelineTemplateReader } from '../PipelineTemplateReader';
import { DeletePipelineTemplateV2Modal } from './DeletePipelineTemplateV2Modal';

import './PipelineTemplatesV2.less';

const { idForTemplate } = PipelineTemplateV2Service;

export interface IPipelineTemplatesV2State {
  fetchError: string;
  searchValue: string;
  viewTemplateId?: string;
  deleteTemplateId?: string;
  selectedTemplate: IPipelineTemplateV2;
  templates: IPipelineTemplateV2[];
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

  public state: IPipelineTemplatesV2State = {
    fetchError: null,
    searchValue: '',
    selectedTemplate: null,
    templates: [],
    viewTemplateId: ReactInjector.$stateParams.templateId,
  };

  public componentDidMount() {
    this.fetchTemplates();
    this.routeChangedSubscription = ReactInjector.stateEvents.stateChangeSuccess.subscribe(this.onRouteChanged);
  }

  public componentWillUnmount() {
    this.routeChangedSubscription.unsubscribe();
  }

  private fetchTemplates() {
    PipelineTemplateReader.getV2PipelineTemplateList().then(
      templates => this.setState({ templates }),
      err => {
        const errorString = get(err, 'data.message', get(err, 'message', ''));
        if (errorString) {
          this.setState({ fetchError: errorString });
        } else {
          this.setState({ fetchError: 'An unknown error occurred while fetching templates' });
        }
      },
    );
  }

  private onRouteChanged = (stateChange: IStateChange) => {
    const { to, toParams } = stateChange;
    if (to.name === 'home.pipeline-templates') {
      this.setState({ viewTemplateId: null });
    } else if (to.name === 'home.pipeline-templates.pipeline-templates-detail') {
      const { templateId } = toParams as { templateId?: string };
      this.setState({ viewTemplateId: templateId || null });
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
    return templates.reduce((s, t) => s + idForTemplate(t), '') + query;
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

  private getViewedTemplate = () => this.state.templates.find(t => idForTemplate(t) === this.state.viewTemplateId);

  private getTemplateToDelete = () => this.state.templates.find(t => idForTemplate(t) === this.state.deleteTemplateId);

  private handleCreatePipelineClick(template: IPipelineTemplateV2): void {
    this.setState({ selectedTemplate: template });
  }

  public handleCreatePipelineModalClose = () => {
    this.setState({ selectedTemplate: null });
  };

  public render() {
    const { templates, selectedTemplate, searchValue, fetchError, viewTemplateId, deleteTemplateId } = this.state;
    const detailsTemplate = viewTemplateId ? this.getViewedTemplate() : null;
    const searchPerformed = searchValue.trim() !== '';
    const filteredResults = this.sortTemplates(this.filterSearchResults(templates, searchValue));
    const resultsAvailable = filteredResults.length > 0;
    const deleteTemplate = deleteTemplateId ? this.getTemplateToDelete() : null;

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
          <div className="infrastructure-section container">
            {fetchError && (
              <PipelineTemplatesV2Error message={`There was an error fetching pipeline templates: ${fetchError}`} />
            )}
            {searchPerformed && !resultsAvailable && (
              <h4 className="infrastructure-section__message">No matches found for '{searchValue}'</h4>
            )}
            {resultsAvailable && (
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
                    const id = idForTemplate(template);
                    return (
                      <tr key={id}>
                        <td>{metadata.name || '-'}</td>
                        <td>{metadata.owner || '-'}</td>
                        <td>{this.getUpdateTimeForTemplate(template) || '-'}</td>
                        <td className="pipeline-template-actions">
                          <UISref to={`.pipeline-templates-detail`} params={{ templateId: id }}>
                            <button className="link">View</button>
                          </UISref>
                          <button className="link" onClick={() => this.setState({ deleteTemplateId: id })}>
                            Delete
                          </button>
                          <button
                            className="link link--create"
                            onClick={() => this.handleCreatePipelineClick(template)}
                          >
                            Create Pipeline
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
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
        {deleteTemplate && (
          <DeletePipelineTemplateV2Modal
            template={deleteTemplate}
            onClose={() => {
              this.fetchTemplates();
              this.setState({ deleteTemplateId: null });
            }}
          />
        )}
        {selectedTemplate && (
          <CreatePipelineFromTemplate
            closeModalCallback={this.handleCreatePipelineModalClose}
            template={selectedTemplate}
          />
        )}
      </>
    );
  }
}

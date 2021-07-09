import { UISref } from '@uirouter/react';
import { flatMap, get, memoize } from 'lodash';
import { DateTime } from 'luxon';
import React from 'react';
import { Modal } from 'react-bootstrap';
import { Subscription } from 'rxjs';

import { DeletePipelineTemplateV2Modal } from './DeletePipelineTemplateV2Modal';
import { PipelineTemplateReader } from '../PipelineTemplateReader';
import { ShowPipelineTemplateJsonModal } from '../../actions/templateJson/ShowPipelineTemplateJsonModal';
import { CreatePipelineFromTemplate } from './createPipelineFromTemplate';
import {
  IPipelineTemplateV2,
  IPipelineTemplateV2Collections,
  IPipelineTemplateV2VersionSelections,
} from '../../../../domain/IPipelineTemplateV2';
import { PipelineTemplateV2Service } from './pipelineTemplateV2.service';
import { ReactSelectInput } from '../../../../presentation';
import { IStateChange, ReactInjector } from '../../../../reactShims';

import './PipelineTemplatesV2.less';

const { convertTemplateVersionToId, getTemplateVersion } = PipelineTemplateV2Service;

export interface IPipelineTemplatesV2State {
  fetchError: string;
  searchValue: string;
  viewTemplateVersion?: string;
  deleteTemplateVersion?: string;
  selectedTemplate: IPipelineTemplateV2;
  templateVersionSelections: IPipelineTemplateV2VersionSelections;
  templates: IPipelineTemplateV2Collections;
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
    templates: {},
    viewTemplateVersion: ReactInjector.$stateParams.templateId,
    templateVersionSelections: {},
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
      (templates) => this.setState({ templates, templateVersionSelections: {} }),
      (err) => {
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
      this.setState({ viewTemplateVersion: null });
    } else if (to.name === 'home.pipeline-templates.pipeline-templates-detail') {
      const { templateId } = toParams as { templateId?: string };
      this.setState({ viewTemplateVersion: templateId || null });
    }
  };

  private sortTemplates = (
    templates: Array<[string, IPipelineTemplateV2[]]>,
  ): Array<[string, IPipelineTemplateV2[]]> => {
    return templates.sort(([a]: [string, IPipelineTemplateV2[]], [b]: [string, IPipelineTemplateV2[]]) => {
      const caseInsensitiveA = a.toLowerCase();
      const caseInsensitiveB = b.toLowerCase();
      if (caseInsensitiveA > caseInsensitiveB) {
        return 1;
      } else if (caseInsensitiveA < caseInsensitiveB) {
        return -1;
      }
      return 0;
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
  private filterMemoizeResolver = (
    templateCollections: Array<[string, IPipelineTemplateV2[]]>,
    query: string,
  ): string => {
    const templateIds = flatMap(templateCollections, ([, templateCollection]) =>
      templateCollection.map((template) => getTemplateVersion(template)),
    );
    return `${templateIds.join('')}${query}`;
  };

  private filterSearchResults = memoize(
    (
      templateCollections: Array<[string, IPipelineTemplateV2[]]>,
      query: string,
    ): Array<[string, IPipelineTemplateV2[]]> => {
      const searchValue = query.trim().toLowerCase();

      if (!searchValue) {
        return templateCollections;
      } else {
        return templateCollections.filter(([, templateCollection]) =>
          templateCollection.some(
            ({ metadata: { name = '', description = '', owner = '' } = {} }) =>
              name.toLowerCase().includes(searchValue) ||
              description.toLowerCase().includes(searchValue) ||
              owner.toLowerCase().includes(searchValue),
          ),
        );
      }
    },
    this.filterMemoizeResolver,
  );

  private getViewTemplate = () => this.findTemplate('viewTemplateVersion');
  private getDeleteTemplate = () => this.findTemplate('deleteTemplateVersion');

  private findTemplate(actionKey: 'viewTemplateVersion' | 'deleteTemplateVersion') {
    const { [actionKey]: templateVersion, templates } = this.state;
    const templateId = convertTemplateVersionToId(templateVersion);
    const { [templateId]: templateCollection = [] } = templates;
    return templateCollection.find((template) => getTemplateVersion(template) === templateVersion);
  }

  private handleCreatePipelineClick(template: IPipelineTemplateV2): void {
    this.setState({ selectedTemplate: template });
  }

  public handleCreatePipelineModalClose = () => {
    this.setState({ selectedTemplate: null });
  };

  private handleSelectedTemplateVersionChange = (e: React.ChangeEvent<HTMLSelectElement>, templateId: string) =>
    this.setState({
      templateVersionSelections: { ...this.state.templateVersionSelections, [templateId]: e.target.value },
    });

  public render() {
    const {
      templates,
      selectedTemplate,
      searchValue,
      fetchError,
      viewTemplateVersion,
      deleteTemplateVersion,
      templateVersionSelections,
    } = this.state;
    const detailsTemplate = viewTemplateVersion ? this.getViewTemplate() : null;
    const searchPerformed = searchValue.trim() !== '';
    const filteredResults = this.sortTemplates(this.filterSearchResults(Object.entries(templates), searchValue));
    const resultsAvailable = filteredResults.length > 0;
    const deleteTemplate = deleteTemplateVersion ? this.getDeleteTemplate() : null;

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
                  ref={(input) => input && input.focus()}
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
              <table className="table templates-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Owner</th>
                    <th>Updated</th>
                    <th>
                      <span className="sort-toggle clickable">
                        Version
                        <span className="glyphicon glyphicon-Down-triangle" />
                      </span>
                    </th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredResults.map(([templateId, templateCollection]) => {
                    const templateVersion =
                      templateVersionSelections[templateId] || getTemplateVersion(templateCollection[0]);
                    const currentTemplate = templateCollection.find(
                      (template) => getTemplateVersion(template) === templateVersion,
                    );
                    const { metadata } = currentTemplate;

                    return (
                      <tr key={templateVersion}>
                        <td className="templates-table__template-name">{metadata.name || '-'}</td>
                        <td>{metadata.owner || '-'}</td>
                        <td>{this.getUpdateTimeForTemplate(currentTemplate) || '-'}</td>
                        <td className="templates-table__template-versions">
                          <ReactSelectInput
                            clearable={false}
                            onChange={(e) => this.handleSelectedTemplateVersionChange(e, templateId)}
                            value={templateVersion}
                            stringOptions={templateCollection.map((templateOption) =>
                              getTemplateVersion(templateOption),
                            )}
                          />
                        </td>
                        <td className="templates-table__template-actions">
                          <UISref to={`.pipeline-templates-detail`} params={{ templateId: templateVersion }}>
                            <button className="link">View</button>
                          </UISref>
                          <button
                            className="link"
                            onClick={() => this.setState({ deleteTemplateVersion: templateVersion })}
                          >
                            Delete
                          </button>
                          <button
                            className="link link--create"
                            onClick={() => this.handleCreatePipelineClick(currentTemplate)}
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
              modalHeading="View Pipeline Template"
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
              this.setState({ deleteTemplateVersion: null });
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

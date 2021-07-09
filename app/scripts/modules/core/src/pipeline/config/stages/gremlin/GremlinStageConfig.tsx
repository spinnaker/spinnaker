import React from 'react';
import Select from 'react-select';
import { forkJoin as observableForkJoin, from as observableFrom } from 'rxjs';

import { REST } from '../../../../api/ApiService';

import { IStageConfigProps, StageConfigField } from '../common';

interface IArgOptions {
  value?: string;
}

interface IState {
  isFetchingData: boolean;
  commands?: any[];
  targets?: any[];
  apiError?: any;
}

interface IAPIResponse {
  status: number;
}

export class GremlinStageConfig extends React.Component<IStageConfigProps> {
  public state: IState = { isFetchingData: false, apiError: '', commands: [], targets: [] };

  public apiErrorMessages = {
    notFound: 'API key does not exist.',
    notEnabled:
      'Gremlin not enabled. Please set integrations.gremlin.enabled to true in your Orca and Gate configuration.',
  };

  public componentDidMount() {
    this.checkInitialLoad();
  }

  private checkInitialLoad = () => {
    const { stage } = this.props;

    if (stage.gremlinApiKey) {
      this.fetchAPIData();
    }
  };

  private fetchCommands = (apiKey: string) => {
    return observableFrom(
      REST('/integrations/gremlin/templates/command')
        .post({
          apiKey,
        })
        .catch((response: IAPIResponse) => {
          this.props.updateStageField({ gremlinCommandTemplateId: null });

          if (response.status === 401) {
            return this.apiErrorMessages.notFound;
          }

          return this.apiErrorMessages.notEnabled;
        }),
    );
  };

  private fetchTargets = (apiKey: string) => {
    return observableFrom(
      REST('/integrations/gremlin/templates/target')
        .post({
          apiKey,
        })
        .catch((response: IAPIResponse) => {
          this.props.updateStageField({ gremlinTargetTemplateId: null });

          if (response.status === 401) {
            return this.apiErrorMessages.notFound;
          }

          return this.apiErrorMessages.notEnabled;
        }),
    );
  };

  private fetchAPIData = () => {
    const {
      stage: { gremlinApiKey },
    } = this.props;

    this.setState({
      isFetchingData: true,
      apiError: '',
    });

    // Get the data from all the necessary sources before rendering
    observableForkJoin(this.fetchCommands(gremlinApiKey), this.fetchTargets(gremlinApiKey)).subscribe((results) => {
      const newState: IState = {
        isFetchingData: false,
      };

      if (Array.isArray(results[0]) && Array.isArray(results[1])) {
        newState.commands = results[0];
        newState.targets = results[1];
      } else {
        newState.apiError = results[0];
        newState.commands = [];
        newState.targets = [];
      }

      this.setState(newState);
    });
  };

  private onChange = (name: string, value: string) => {
    this.props.updateStageField({ [name]: value });
  };

  private handleGremlinCommandTemplateIdChange = (option: IArgOptions) => {
    this.props.updateStageField({ gremlinCommandTemplateId: option.value });
  };

  private handleGremlinTargetTemplateIdChange = (option: IArgOptions) => {
    this.props.updateStageField({ gremlinTargetTemplateId: option.value });
  };

  public render() {
    const { stage } = this.props;
    const { isFetchingData, apiError, commands, targets } = this.state;

    // Provides access to meta information for summary box
    const selectedCommandTemplateMeta =
      commands.length && stage.gremlinCommandTemplateId
        ? commands.find((command) => command.guid === stage.gremlinCommandTemplateId)
        : {};
    const selectedTargetTemplateMeta =
      targets.length && stage.gremlinTargetTemplateId
        ? targets.find((target) => target.guid === stage.gremlinTargetTemplateId)
        : {};

    return (
      <>
        <div className="form-horizontal">
          <StageConfigField label="API Key">
            <input
              name="gremlinApiKey"
              className="form-control input"
              type="text"
              value={stage.gremlinApiKey || ''}
              onChange={(e) => this.onChange(e.target.name, e.target.value)}
            />
            <div className="form-control-static" style={{ paddingBottom: 0 }}>
              <div className="flex-container-h middle margin-between-md">
                <button
                  disabled={isFetchingData || !stage.gremlinApiKey}
                  onClick={this.fetchAPIData}
                  type="button"
                  className="btn btn-sm btn-default"
                >
                  {isFetchingData ? 'Loading...' : 'Fetch'}
                </button>
                {apiError && <span className="text-danger text-small">{apiError}</span>}
              </div>
            </div>
            <div className="form-control-static">
              <a
                className="text-small"
                target="_blank"
                rel="noopener noreferrer"
                href="https://app.gremlin.com/api-keys"
              >
                Create a Gremlin API key
              </a>
            </div>
          </StageConfigField>
          <StageConfigField label="Target Template">
            {!targets.length ? (
              isFetchingData ? (
                <p className="form-control-static">Loading...</p>
              ) : (
                <p className="form-control-static">No targets found.</p>
              )
            ) : (
              <Select
                name="gremlinTargetTemplateId"
                options={targets.map((target) => ({
                  label: target.name,
                  value: target.guid,
                }))}
                clearable={false}
                value={stage.gremlinTargetTemplateId || null}
                onChange={this.handleGremlinTargetTemplateIdChange}
              />
            )}
          </StageConfigField>
          <StageConfigField label="Attack Template">
            {!commands.length ? (
              isFetchingData ? (
                <p className="form-control-static">Loading...</p>
              ) : (
                <p className="form-control-static">No commands found.</p>
              )
            ) : (
              <Select
                name="gremlinCommandTemplateId"
                options={commands.map((command) => ({
                  label: command.name,
                  value: command.guid,
                }))}
                clearable={false}
                value={stage.gremlinCommandTemplateId || null}
                onChange={this.handleGremlinCommandTemplateIdChange}
              />
            )}
            <div className="form-control-static">
              <a
                className="text-small"
                target="_blank"
                rel="noopener noreferrer"
                href="https://docs.gremlin.com/attacks/#how-to-create-attack-templates-with-gremlin"
              >
                How to create Gremlin templates
              </a>
            </div>
          </StageConfigField>
        </div>
        {(stage.gremlinCommandTemplateId || stage.gremlinTargetTemplateId) && (
          <>
            <hr />
            <h3>Summary</h3>
            <div className="list-group">
              {stage.gremlinTargetTemplateId && (
                <div className="list-group-item">
                  <h4 className="list-group-item-heading">Target Template ({selectedTargetTemplateMeta.name})</h4>
                  <p className="list-group-item-text">{selectedTargetTemplateMeta.synthetic_description}</p>
                </div>
              )}
              {stage.gremlinCommandTemplateId && (
                <div className="list-group-item">
                  <h4 className="list-group-item-heading">Attack Template ({selectedCommandTemplateMeta.name})</h4>
                  <p className="list-group-item-text">{selectedCommandTemplateMeta.synthetic_description}</p>
                </div>
              )}
            </div>
          </>
        )}
      </>
    );
  }
}

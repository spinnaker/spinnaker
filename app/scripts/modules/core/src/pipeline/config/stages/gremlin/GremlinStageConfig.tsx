import * as React from 'react';

import { API } from 'core/api/ApiService';
import { IStageConfigProps, StageConfigField } from 'core/pipeline';
import { Observable } from 'rxjs';
import Select from 'react-select';

interface IArgOptions {
  value?: string;
}

interface IState {
  isFetchingData: boolean;
  commands: any[];
  targets: any[];
}

export class GremlinStageConfig extends React.Component<IStageConfigProps> {
  public state: IState = { isFetchingData: false, commands: [], targets: [] };

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
    return Observable.fromPromise(
      API.one('gremlin/templates/command')
        .post({
          apiKey,
        })
        .catch(() => [] as any[]),
    );
  };

  private fetchTargets = (apiKey: string) => {
    return Observable.fromPromise(
      API.one('gremlin/templates/target')
        .post({
          apiKey,
        })
        .catch(() => [] as any[]),
    );
  };

  private fetchAPIData = () => {
    const {
      stage: { gremlinApiKey },
    } = this.props;

    this.setState({
      isFetchingData: true,
    });

    // Get the data from all the necessary sources before rendering
    Observable.forkJoin(this.fetchCommands(gremlinApiKey), this.fetchTargets(gremlinApiKey)).subscribe(results => {
      this.setState({
        commands: results[0],
        targets: results[1],
        isFetchingData: false,
      });
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
    const { isFetchingData, commands, targets } = this.state;

    // Provides access to meta information for summary box
    const selectedCommandTemplateMeta = stage.gremlinCommandTemplateId
      ? commands.find(command => command.guid === stage.gremlinCommandTemplateId)
      : {};
    const selectedTargetTemplateMeta = stage.gremlinTargetTemplateId
      ? targets.find(target => target.guid === stage.gremlinTargetTemplateId)
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
              onChange={e => this.onChange(e.target.name, e.target.value)}
            />
            <div className="form-control-static">
              <button
                disabled={isFetchingData || !stage.gremlinApiKey}
                onClick={this.fetchAPIData}
                type="button"
                className="btn btn-sm btn-default"
              >
                {isFetchingData ? 'Loading...' : 'Fetch'}
              </button>
            </div>
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
                options={commands.map(command => ({
                  label: command.name,
                  value: command.guid,
                }))}
                clearable={false}
                value={stage.gremlinCommandTemplateId || null}
                onChange={this.handleGremlinCommandTemplateIdChange}
              />
            )}
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
                options={targets.map(target => ({
                  label: target.name,
                  value: target.guid,
                }))}
                clearable={false}
                value={stage.gremlinTargetTemplateId || null}
                onChange={this.handleGremlinTargetTemplateIdChange}
              />
            )}
          </StageConfigField>
          <StageConfigField label="&nbsp;">
            <a
              className="text-small"
              target="_blank"
              rel="noopener noreferrer"
              href="https://docs.gremlin.com/attacks/#how-to-create-attack-templates-with-gremlin"
            >
              How to create attack templates with Gremlin
            </a>
          </StageConfigField>
        </div>
        {(stage.gremlinCommandTemplateId || stage.gremlinTargetTemplateId) && (
          <>
            <hr />
            <h3>Summary</h3>
            <div className="list-group">
              {stage.gremlinCommandTemplateId && (
                <div className="list-group-item">
                  <h4 className="list-group-item-heading">Attack Template ({selectedCommandTemplateMeta.name})</h4>
                  <p className="list-group-item-text">{selectedCommandTemplateMeta.synthetic_description}</p>
                </div>
              )}
              {stage.gremlinTargetTemplateId && (
                <div className="list-group-item">
                  <h4 className="list-group-item-heading">Target Template ({selectedTargetTemplateMeta.name})</h4>
                  <p className="list-group-item-text">{selectedTargetTemplateMeta.synthetic_description}</p>
                </div>
              )}
            </div>
          </>
        )}
      </>
    );
  }
}

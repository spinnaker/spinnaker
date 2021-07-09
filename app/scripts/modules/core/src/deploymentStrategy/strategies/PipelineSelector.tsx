import React from 'react';
import { Option } from 'react-select';
import VirtualizedSelect from 'react-virtualized-select';

import { ApplicationReader } from '../../application/service/ApplicationReader';
import { IParameter, IPipeline } from '../../domain';
import { HelpField } from '../../help/HelpField';
import { PipelineConfigService } from '../../pipeline/config/services/PipelineConfigService';
import { Spinner } from '../../widgets';

interface IPipelineSelectorCommand {
  application?: string;
  pipelineId?: string;
  pipelineParameters?: { [key: string]: any };

  strategyPipeline?: string;
  strategyApplication?: string;
}

export interface IPipelineSelectorProps {
  command: IPipelineSelectorCommand;
  type: 'pipelines' | 'strategies';
}

export interface IPipelineSelectorState {
  applicationsLoaded: boolean;
  pipelinesLoaded: boolean;
  applicationOptions: Array<Option<string>>;
  pipelines: IPipeline[];
  pipelineOptions: Option[];
  pipelineParameters?: IParameter[];
  useDefaultParameters: { [key: string]: boolean };
  userSuppliedParameters: { [key: string]: any };
}

export class PipelineSelector extends React.Component<IPipelineSelectorProps, IPipelineSelectorState> {
  public state: IPipelineSelectorState = {
    applicationsLoaded: false,
    pipelinesLoaded: false,
    applicationOptions: [],
    pipelineOptions: [],
    pipelines: [],
    pipelineParameters: [],
    useDefaultParameters: {},
    userSuppliedParameters: {},
  };

  private clearParams(): void {
    this.setState({ pipelineParameters: [], useDefaultParameters: {}, userSuppliedParameters: {} });
  }

  private configureParamDefaults(pipelineParameters: IParameter[]): void {
    const useDefaultParameters = this.state.useDefaultParameters;
    pipelineParameters.forEach((param: any) => {
      const defaultValue = param.default;
      if (defaultValue !== null && defaultValue !== undefined) {
        const configuredParamValue = this.props.command.pipelineParameters[param.name];
        if (configuredParamValue === undefined || configuredParamValue === defaultValue) {
          useDefaultParameters[param.name] = true;
          this.props.command.pipelineParameters[param.name] = defaultValue;
        }
      }
    });
    this.setState({ useDefaultParameters });
  }

  public updatePipelineConfig(pipelines: IPipeline[]): void {
    const { command, type } = this.props;

    const pipelineId = type === 'pipelines' ? command.pipelineId : command.strategyPipeline;

    if (command && pipelineId) {
      const config = pipelines.find((p) => p.id === pipelineId);
      if (config && config.parameterConfig) {
        if (!command.pipelineParameters) {
          command.pipelineParameters = {};
        }
        this.setState({
          pipelineParameters: config.parameterConfig,
          userSuppliedParameters: command.pipelineParameters,
          useDefaultParameters: {},
        });
        this.configureParamDefaults(this.state.pipelineParameters);
      } else {
        this.clearParams();
      }
    } else {
      this.clearParams();
    }
  }

  protected validateCurrentPipeline(pipelines: IPipeline[]) {
    const { command, type } = this.props;

    if (type === 'pipelines' && pipelines.every((p) => p.id !== command.pipelineId)) {
      command.pipelineId = '';
    }
    if (type === 'strategies' && pipelines.every((p) => p.id !== command.strategyPipeline)) {
      command.pipelineId = '';
    }
  }

  private updatePipelines(pipelines: IPipeline[]) {
    const pipelinesLoaded = true;
    this.updatePipelineConfig(pipelines);
    const pipelineOptions = pipelines.map((p) => ({ label: p.name, value: p.id }));
    this.setState({ pipelines, pipelinesLoaded, pipelineOptions });
  }

  public initializePipelines(): void {
    this.setState({ pipelinesLoaded: false });
    const { command, type } = this.props;
    if (type === 'pipelines' && command.application) {
      PipelineConfigService.getPipelinesForApplication(command.application).then((pipelines) => {
        this.validateCurrentPipeline(pipelines);
        this.updatePipelines(pipelines);
      });
    } else if (type === 'strategies' && command.strategyApplication) {
      PipelineConfigService.getStrategiesForApplication(command.strategyApplication).then((pipelines) => {
        this.validateCurrentPipeline(pipelines);
        this.updatePipelines(pipelines);
      });
    } else {
      this.forceUpdate();
    }
  }

  public componentDidMount() {
    if (this.props.type === 'strategies' && !this.props.command.strategyApplication) {
      this.props.command.strategyApplication = this.props.command.application;
    }

    ApplicationReader.listApplications().then((applications) => {
      const applicationOptions = applications
        .map((a) => a.name)
        .sort()
        .map((a) => ({ label: a, value: a }));
      this.setState({ applicationOptions, applicationsLoaded: true });
      this.initializePipelines();
    });
  }

  private applicationChange = (option: Option<string>) => {
    if (this.props.type === 'pipelines') {
      this.props.command.application = option ? option.value : '';
    } else {
      this.props.command.strategyApplication = option ? option.value : '';
    }
    this.initializePipelines();
  };

  private pipelineChange = (option: Option<string>) => {
    if (this.props.type === 'pipelines') {
      this.props.command.pipelineId = option ? option.value : '';
    } else {
      this.props.command.strategyPipeline = option ? option.value : '';
    }

    this.updatePipelineConfig(this.state.pipelines);
  };

  public updateParam(parameter: string, value: any): void {
    const { userSuppliedParameters } = this.state;
    userSuppliedParameters[parameter] = value;

    if (this.state.useDefaultParameters[parameter] === true) {
      delete userSuppliedParameters[parameter];
      delete this.props.command.pipelineParameters[parameter];
    } else if (this.state.userSuppliedParameters[parameter]) {
      this.props.command.pipelineParameters[parameter] = this.state.userSuppliedParameters[parameter];
    }
    this.setState({ userSuppliedParameters });
  }

  public render() {
    const { command, type } = this.props;
    const {
      applicationsLoaded,
      applicationOptions,
      pipelineParameters,
      pipelineOptions,
      pipelinesLoaded,
      useDefaultParameters,
      userSuppliedParameters,
    } = this.state;
    const application = type === 'pipelines' ? command.application : command.strategyApplication;
    const pipelineId = type === 'pipelines' ? command.pipelineId : command.strategyPipeline;

    return (
      <div className="form-group">
        <div className="form-horizontal">
          <div className="form-group">
            <label className="col-md-2 col-md-offset-1 sm-label-right">Application</label>
            <div className="col-md-6">
              {!applicationsLoaded && (
                <div className="sp-margin-xs-top">
                  <Spinner size="small" />
                </div>
              )}
              {applicationsLoaded && (
                <VirtualizedSelect
                  placeholder="None"
                  value={application}
                  options={applicationOptions}
                  onChange={this.applicationChange}
                />
              )}
            </div>
          </div>

          {application && pipelinesLoaded && (
            <div className="form-group">
              <label className="col-md-2 col-md-offset-1 sm-label-right">Pipeline</label>
              <div className="col-md-6">
                <div>
                  <VirtualizedSelect
                    placeholder="Select a pipeline..."
                    value={pipelineId}
                    options={pipelineOptions}
                    onChange={this.pipelineChange as any}
                  />
                </div>
              </div>
            </div>
          )}

          {pipelineParameters.length > 0 && (
            <div className="well well-sm clearfix ng-scope col-md-12">
              <strong className="text-left">Parameters</strong>
              {pipelineParameters.map((parameter) => (
                <div key={parameter.name} className="form-group">
                  <div className="col-md-3 sm-label-right">
                    {!parameter.description && <span>{parameter.name}</span>}
                    {parameter.description && <HelpField content={parameter.description} label={parameter.name} />}
                  </div>
                  <div className="col-md-6">
                    {useDefaultParameters[parameter.name] && (
                      <input disabled={true} type="text" className="form-control input-sm" value={parameter.default} />
                    )}
                    {useDefaultParameters[parameter.name] && !parameter.hasOptions && (
                      <input
                        type="text"
                        className="form-control input-sm"
                        value={userSuppliedParameters[parameter.name]}
                        onChange={(e) => this.updateParam(parameter.name, e.target.value)}
                      />
                    )}
                    {!useDefaultParameters[parameter.name] && parameter.hasOptions && (
                      <VirtualizedSelect
                        style={{ width: '100%' }}
                        value={userSuppliedParameters[parameter.name]}
                        onChange={(o: Option<string>) => this.updateParam(parameter.name, o ? o.value : '')}
                        options={parameter.options.map((o) => ({ label: o.value, value: o.value }))}
                      />
                    )}
                  </div>
                  {parameter.default !== null && parameter.default !== undefined && (
                    <div className="checkbox col-md-3">
                      <label>
                        <input
                          type="checkbox"
                          checked={useDefaultParameters[parameter.name]}
                          onChange={(e) => this.updateParam(parameter.name, e.target.checked)}
                        />
                        Use default
                      </label>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    );
  }
}

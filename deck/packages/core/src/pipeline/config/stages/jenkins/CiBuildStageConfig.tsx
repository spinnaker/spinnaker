import { pickBy } from 'lodash';
import React from 'react';
import type { Option } from 'react-select';
import Select from 'react-select';

import { AddCiBuildParameterModal } from './AddCiBuildParameterModal';
import type { BuildServiceType } from '../../../../ci/igor.service';
import { IgorService } from '../../../../ci/igor.service';
import type { IStageConfigProps } from '../common';
import { StageConfigField } from '../common';
import type { IJobConfig, IParameterDefinitionList } from '../../../../domain';
import { HelpField } from '../../../../help';

export interface ICiBuildStageConfigProps extends IStageConfigProps {
  buildServiceType: BuildServiceType;
  buildServiceLabel: string;
  buildServicePlaceholder: string;
  jobLabel?: string;
  jobPlaceholder?: string;
  propertyFileHelpKey?: string;
  parametersHelpKey?: string;
  waitForCompletionHelpKey: string;
  markUnstableHelpKeyPrefix: string;
  showJenkinsParameters?: boolean;
  showInlineParameters?: boolean;
  info?: React.ReactNode;
}

interface ICiBuildStageConfigState {
  masters: string[];
  jobs: string[];
  jobParams: IParameterDefinitionList[];
  invalidParameters: { [key: string]: any };
  useDefaultParameters: { [key: string]: boolean };
  mastersRefreshing: boolean;
  jobsRefreshing: boolean;
  jobsLoaded: boolean;
}

export class CiBuildStageConfig extends React.Component<ICiBuildStageConfigProps, ICiBuildStageConfigState> {
  private filterThreshold = 500;
  private filterLimit = 100;
  private mounted = false;

  constructor(props: ICiBuildStageConfigProps) {
    super(props);
    this.state = {
      masters: [],
      jobs: [],
      jobParams: [],
      invalidParameters: {},
      useDefaultParameters: {},
      mastersRefreshing: false,
      jobsRefreshing: false,
      jobsLoaded: false,
    };
  }

  public componentDidMount(): void {
    this.mounted = true;
    const stage = this.props.stage as any;
    this.setDefault('failPipeline', true);
    this.setDefault('continuePipeline', false);
    this.refreshMasters();
    if (stage.master) {
      this.updateJobsList();
    }
    if (stage.job) {
      this.updateJobConfig();
    }
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  public render() {
    const stage = this.props.stage as any;
    const waitForCompletion = stage.waitForCompletion === undefined ? true : stage.waitForCompletion;

    return (
      <>
        <div className="form-horizontal">
          <StageConfigField label={this.props.buildServiceLabel}>
            {this.isParameterized(stage.master) ? (
              <p className="form-control-static">{stage.master}</p>
            ) : (
              <div className="flex-container-h middle">
                <Select
                  value={stage.master}
                  placeholder={this.props.buildServicePlaceholder}
                  options={this.state.masters.map((master) => ({ label: master, value: master }))}
                  onChange={this.onMasterChanged}
                  clearable={false}
                />
                <button
                  className="btn btn-link"
                  type="button"
                  onClick={this.refreshMasters}
                  title="Refresh masters list"
                >
                  <span className={`fa fa-sync-alt ${this.state.mastersRefreshing ? 'fa-spin' : ''}`} />
                </button>
              </div>
            )}
          </StageConfigField>

          {this.renderJobField(stage)}

          {this.props.propertyFileHelpKey && (
            <StageConfigField label="Property File" helpKey={this.props.propertyFileHelpKey}>
              <input
                type="text"
                className="form-control input-sm"
                value={stage.propertyFile || ''}
                onChange={(event) => this.updateStageField({ propertyFile: event.target.value })}
              />
            </StageConfigField>
          )}

          {this.props.showJenkinsParameters && this.renderJenkinsParameters(stage)}
          {this.props.showInlineParameters && this.renderInlineParameters(stage)}

          <StageConfigField label="Wait for results" helpKey={this.props.waitForCompletionHelpKey}>
            <input
              type="checkbox"
              className="input-sm"
              name="waitForCompletion"
              checked={waitForCompletion}
              onChange={(event) => this.updateStageField({ waitForCompletion: event.target.checked })}
            />
          </StageConfigField>

          <div className="form-group">
            <label className="col-md-2 col-md-offset-1 sm-label-right">If build is unstable</label>
            <div className="col-md-9">
              <div className="radio">
                <label>
                  <input
                    type="radio"
                    checked={!stage.markUnstableAsSuccessful}
                    onChange={() => this.updateStageField({ markUnstableAsSuccessful: false })}
                  />
                  fail the stage
                  <HelpField id={`${this.props.markUnstableHelpKeyPrefix}.false`} />
                </label>
              </div>
              <div className="radio">
                <label>
                  <input
                    type="radio"
                    checked={!!stage.markUnstableAsSuccessful}
                    onChange={() => this.updateStageField({ markUnstableAsSuccessful: true })}
                  />
                  consider stage successful
                  <HelpField id={`${this.props.markUnstableHelpKeyPrefix}.true`} />
                </label>
              </div>
            </div>
          </div>
        </div>
        {this.props.info}
      </>
    );
  }

  private renderJobField(stage: any) {
    const jobIsParameterized = this.isParameterized(stage.job);
    return (
      <StageConfigField label={this.props.jobLabel || 'Job'}>
        {!stage.master && <p className="form-control-static">(Select a build service)</p>}
        {stage.master && (this.isParameterized(stage.master) || jobIsParameterized) && (
          <p className="form-control-static">{stage.job}</p>
        )}
        {stage.master && !this.isParameterized(stage.master) && !jobIsParameterized && (
          <div className="flex-container-h middle">
            <Select
              value={stage.job}
              placeholder={this.shouldFilter() ? 'Start typing...' : this.props.jobPlaceholder || 'Select a job...'}
              options={this.state.jobs.map((job) => ({ label: job, value: job }))}
              filterOptions={this.shouldFilter() ? this.filterJobOptions : undefined}
              onChange={this.onJobChanged}
              clearable={false}
            />
            <button className="btn btn-link" type="button" onClick={this.refreshJobs} title="Refresh job list">
              <span className={`fa fa-sync-alt ${this.state.jobsRefreshing ? 'fa-spin' : ''}`} />
            </button>
          </div>
        )}
      </StageConfigField>
    );
  }

  private renderJenkinsParameters(stage: any) {
    const params = this.state.jobParams || [];
    if (!params.length) {
      return null;
    }
    return (
      <div className="well well-sm clearfix col-md-offset-1 col-md-10">
        <h4 className="text-left">Job Parameters</h4>
        {params
          .slice()
          .sort((a, b) => a.name.localeCompare(b.name))
          .map((parameter: any) => this.renderJenkinsParameter(stage, parameter))}
        {!!Object.keys(this.state.invalidParameters).length && (
          <div className="alert alert-danger vertical">
            <p>
              <i className="fa fa-exclamation-triangle" /> The following parameters are not accepted by the jenkins job
              but are still set in the stage configuration:
            </p>
            {Object.entries(this.state.invalidParameters).map(([paramName, paramValue]) => (
              <div key={paramName} className="flex-container-h" style={{ margin: '0.5em' }}>
                <label className="col-md-2">{paramName}</label>
                <input className="flex-grow" type="text" style={{ width: '100%' }} disabled value={paramValue as any} />
              </div>
            ))}
            <button className="self-right passive" type="button" onClick={this.removeInvalidParameters}>
              Remove all
            </button>
          </div>
        )}
      </div>
    );
  }

  private renderJenkinsParameter(stage: any, parameter: any) {
    const usingDefault = this.state.useDefaultParameters[parameter.name];
    const value = usingDefault ? parameter.defaultValue || '' : (stage.parameters || {})[parameter.name] || '';
    return (
      <div className="form-group" key={parameter.name}>
        <div className="col-md-3 sm-label-right">
          <b className="break-word">{parameter.name}</b>
          {parameter.description && <HelpField content={parameter.description} />}
        </div>
        <div className="col-md-5">
          {parameter.type === 'BooleanParameterDefinition' ? (
            <input
              type="checkbox"
              className="input-sm"
              disabled={usingDefault}
              checked={value === true || value === 'true'}
              onChange={(event) => this.updateParameter(parameter.name, event.target.checked)}
            />
          ) : parameter.type === 'TextParameterDefinition' ? (
            <textarea
              className="form-control input-sm"
              disabled={usingDefault}
              value={value}
              onChange={(event) => this.updateParameter(parameter.name, event.target.value)}
              style={{ maxWidth: '100%' }}
            />
          ) : parameter.type === 'ChoiceParameterDefinition' ? (
            <Select
              value={value}
              disabled={usingDefault}
              options={(parameter.choices || []).map((choice: string) => ({ label: choice, value: choice }))}
              onChange={(option: Option<string>) => this.updateParameter(parameter.name, option.value)}
              clearable={false}
            />
          ) : (
            <input
              type={parameter.type === 'PasswordParameterDefinition' ? 'password' : 'text'}
              className="form-control input-sm"
              disabled={usingDefault}
              value={value}
              onChange={(event) => this.updateParameter(parameter.name, event.target.value)}
            />
          )}
        </div>
        {parameter.defaultValue !== null && (
          <div className="checkbox col-md-4">
            <label>
              <input
                type="checkbox"
                checked={usingDefault}
                onChange={(event) => this.toggleUseDefault(parameter.name, event.target.checked)}
              />
              Use default
            </label>
          </div>
        )}
      </div>
    );
  }

  private renderInlineParameters(stage: any) {
    const parameters = stage.parameters || {};
    return (
      <StageConfigField label="Parameters" helpKey={this.props.parametersHelpKey}>
        <table className="table table-condensed packed">
          <thead>
            <tr>
              <th style={{ width: '40%' }}>Key</th>
              <th style={{ width: '60%' }}>Value</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {Object.entries(parameters).map(([key, value]) => (
              <tr key={key}>
                <td>
                  <strong className="small">{key}</strong>
                </td>
                <td>
                  <input
                    type="text"
                    required
                    value={value as any}
                    onChange={(event) => this.updateParameter(key, event.target.value)}
                    className="form-control input-sm"
                  />
                </td>
                <td className="text-center">
                  <button className="btn btn-link" type="button" onClick={() => this.removeParameter(key)}>
                    <span className="glyphicon glyphicon-trash" /> <span className="sr-only">Remove</span>
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="row">
          <div className="col-md-12">
            <button className="btn btn-block btn-sm add-new" type="button" onClick={this.addParameter}>
              <span className="glyphicon glyphicon-plus-sign" /> Add Parameter
            </button>
          </div>
        </div>
      </StageConfigField>
    );
  }

  private setDefault(field: string, value: any): void {
    const stage = this.props.stage as any;
    if (stage[field] === undefined) {
      stage[field] = value;
    }
  }

  private setStateIfMounted(state: Partial<ICiBuildStageConfigState>, callback?: () => void): void {
    if (this.mounted) {
      this.setState(state as any, callback);
    }
  }

  private updateStageField(changes: { [key: string]: any }): void {
    this.props.updateStageField(changes);
    this.props.stageFieldUpdated();
  }

  private onMasterChanged = (option: Option<string>) => {
    this.updateStageField({ master: option.value, job: '' });
    this.updateJobsList(option.value);
  };

  private onJobChanged = (option: Option<string>) => {
    this.updateStageField({ job: option.value });
    this.updateJobConfig(option.value);
  };

  private refreshMasters = (): void => {
    this.setStateIfMounted({ mastersRefreshing: true });
    IgorService.listMasters(this.props.buildServiceType)
      .then((masters: string[]) => {
        this.setStateIfMounted({ masters, mastersRefreshing: false });
      })
      .catch(() => this.setStateIfMounted({ mastersRefreshing: false }));
  };

  private refreshJobs = (): void => this.updateJobsList();

  private updateJobsList(master = (this.props.stage as any).master): void {
    const job = (this.props.stage as any).job || '';
    if (!master || this.isParameterized(master) || this.isParameterized(job)) {
      this.setStateIfMounted({ jobsLoaded: true });
      return;
    }
    this.setStateIfMounted({ jobs: [], jobsLoaded: false, jobsRefreshing: true, jobParams: [] });
    IgorService.listJobsForMaster(master)
      .then((jobs: string[]) => {
        const stage = this.props.stage as any;
        if (stage.master !== master) {
          return;
        }
        if (stage.job === job && stage.job && !jobs.includes(stage.job)) {
          this.updateStageField({ job: '' });
        }
        this.setStateIfMounted({ jobs, jobsLoaded: true, jobsRefreshing: false });
      })
      .catch(() => this.setStateIfMounted({ jobsLoaded: true, jobsRefreshing: false }));
  }

  private updateJobConfig(job = (this.props.stage as any).job): void {
    const stage = this.props.stage as any;
    if (!stage.master || !job || this.isParameterized(stage.master) || this.isParameterized(job)) {
      return;
    }
    const master = stage.master;
    IgorService.getJobConfig(stage.master, job)
      .then((config: IJobConfig) => {
        if (stage.master !== master || stage.job !== job) {
          return;
        }
        const jobParams = (config || ({} as IJobConfig)).parameterDefinitionList || [];
        const parameters = stage.parameters || {};
        if (!stage.parameters) {
          stage.parameters = parameters;
        }
        const acceptedJobParameters = jobParams.map((param) => param.name);
        const useDefaultParameters = jobParams.reduce((acc: { [key: string]: boolean }, property: any) => {
          if (!(property.name in parameters) && property.defaultValue !== null) {
            acc[property.name] = true;
          }
          return acc;
        }, {});
        this.setStateIfMounted({
          jobParams,
          useDefaultParameters,
          invalidParameters: pickBy(parameters, (_value, name) => !acceptedJobParameters.includes(name)),
        });
      })
      .catch(() => this.setStateIfMounted({ jobParams: [], useDefaultParameters: {}, invalidParameters: {} }));
  }

  private updateParameter(key: string, value: any): void {
    const parameters = { ...((this.props.stage as any).parameters || {}), [key]: value };
    this.updateStageField({ parameters });
  }

  private toggleUseDefault(key: string, checked: boolean): void {
    const parameters = { ...((this.props.stage as any).parameters || {}) };
    if (checked) {
      delete parameters[key];
    }
    this.updateStageField({ parameters });
    this.setStateIfMounted({ useDefaultParameters: { ...this.state.useDefaultParameters, [key]: checked } });
  }

  private removeInvalidParameters = (): void => {
    const parameters = { ...((this.props.stage as any).parameters || {}) };
    Object.keys(this.state.invalidParameters).forEach((param) => delete parameters[param]);
    this.updateStageField({ parameters });
    this.setStateIfMounted({ invalidParameters: {} });
  };

  private addParameter = (): void => {
    AddCiBuildParameterModal.show()
      .then((parameter) => this.updateParameter(parameter.key, parameter.value))
      .catch(() => {});
  };

  private removeParameter(key: string): void {
    const parameters = { ...((this.props.stage as any).parameters || {}) };
    delete parameters[key];
    this.updateStageField({ parameters });
  }

  private shouldFilter(): boolean {
    return this.state.jobs && this.state.jobs.length >= this.filterThreshold;
  }

  private filterJobOptions = (options: Array<Option<string>>, filter: string): Array<Option<string>> => {
    const normalizedFilter = filter.toLowerCase();
    return options
      .filter((option) => option.label?.toLowerCase().includes(normalizedFilter))
      .slice(0, this.filterLimit);
  };

  private isParameterized(value: string): boolean {
    return !!value && value.includes('${');
  }
}

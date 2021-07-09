import { get, has } from 'lodash';
import React from 'react';
import { Option } from 'react-select';

import { IExecution, IPipeline, IPipelineTrigger } from '../../../../domain';
import { ExecutionBuildTitle } from '../../../executionBuild/ExecutionBuildTitle';
import { ITriggerTemplateComponentProps } from '../../../manualExecution/TriggerTemplate';
import { TetheredSelect } from '../../../../presentation/TetheredSelect';
import { ReactInjector } from '../../../../reactShims';
import { ExecutionsTransformer } from '../../../service/ExecutionsTransformer';
import { PipelineConfigService } from '../../services/PipelineConfigService';
import { timestamp } from '../../../../utils/timeFormatters';
import { Spinner } from '../../../../widgets/spinners/Spinner';

export interface IPipelineTriggerTemplateState {
  executions: IExecution[];
  executionsLoading: boolean;
  loadError: boolean;
  selectedExecution: string;
}

export class PipelineTriggerTemplate extends React.Component<
  ITriggerTemplateComponentProps,
  IPipelineTriggerTemplateState
> {
  public static formatLabel(trigger: IPipelineTrigger): PromiseLike<string> {
    // if this is a re-run, the trigger info will be on the parentExecution; otherwise, check the trigger itself
    // (normalization occurs in the pipelineTriggerOptions component, but that renders after this method is called)
    const application = get(trigger, 'parentExecution.application', trigger.application);
    const pipelineConfigId = get(trigger, 'parentExecution.pipelineConfigId', trigger.pipeline);

    const loadSuccess = (pipelines: IPipeline[]) => {
      const pipeline = pipelines.find((config) => config.id === pipelineConfigId);
      return pipeline ? `(Pipeline) ${application}: ${pipeline.name}` : '[pipeline not found]';
    };

    const loadFailure = () => {
      return `[could not load pipelines for '${application}']`;
    };

    return PipelineConfigService.getPipelinesForApplication(application).then(loadSuccess, loadFailure);
  }

  public constructor(props: ITriggerTemplateComponentProps) {
    super(props);
    this.state = {
      executions: [],
      executionsLoading: true,
      loadError: false,
      selectedExecution: null,
    };
  }

  private initialize = () => {
    const { command } = this.props;
    command.triggerInvalid = true;
    const trigger = command.trigger as IPipelineTrigger;

    // structure is a little different if this is a re-run; need to extract the fields from the parentExecution
    const parent = trigger.parentExecution;
    if (parent) {
      trigger.application = parent.application;
      trigger.pipeline = parent.pipelineConfigId;
      trigger.parentPipelineId = parent.id;
    }

    // These fields will be added to the trigger when the form is submitted
    command.extraFields = {};

    // do not re-initialize if the trigger has changed to some other type
    if (command.trigger.type !== 'pipeline') {
      return;
    }

    ReactInjector.executionService
      .getExecutionsForConfigIds([trigger.pipeline], { limit: 20 })
      .then(this.executionLoadSuccess, this.executionLoadFailure);
  };

  public componentWillReceiveProps(nextProps: ITriggerTemplateComponentProps) {
    if (nextProps.command !== this.props.command) {
      this.initialize();
    }
  }

  public componentDidMount() {
    this.initialize();
  }

  private executionLoadSuccess = (executions: IExecution[]) => {
    const newState = { executions } as IPipelineTriggerTemplateState;
    const trigger = this.props.command.trigger as IPipelineTrigger;

    if (executions.length) {
      executions.forEach((execution) => ExecutionsTransformer.addBuildInfo(execution));
      // default to what is supplied by the trigger if possible; otherwise, use the latest
      const defaultSelection = executions.find((e) => e.id === trigger.parentPipelineId) || executions[0];
      newState.selectedExecution = defaultSelection.id;
      this.updateSelectedExecution(defaultSelection);
    }
    newState.executionsLoading = false;
    this.setState(newState);
  };

  private executionLoadFailure = () => {
    this.setState({
      executionsLoading: false,
      loadError: true,
    });
  };

  private updateSelectedExecution = (item: IExecution) => {
    this.props.command.extraFields.parentPipelineId = item.id;
    this.props.command.extraFields.parentPipelineApplication = item.application;
    this.props.command.triggerInvalid = false;
    this.setState({ selectedExecution: item.id });
  };

  private handleExecutionChanged = (option: Option<string>) => {
    const execution = this.getExecutionFromId(option.value);
    this.updateSelectedExecution(execution);
  };

  private getExecutionFromId = (id: string) => {
    return this.state.executions.find((e) => e.id === id);
  };

  private optionRenderer = (option: Option<string>) => {
    const execution = this.getExecutionFromId(option.value);
    const scm = has(execution, 'buildInfo.scm[0]') && execution.buildInfo.scm[0];

    return (
      <span style={{ fontSize: '12px' }}>
        <strong>
          <ExecutionBuildTitle execution={execution} defaultToTimestamp={false} />{' '}
        </strong>
        {timestamp(execution.buildTime)} ({execution.status})
        {scm && (
          <span>
            <br />
            {scm.branch} ({scm.sha1 && scm.sha1.substr(0, 6)}
            ): {scm.message && scm.message.substr(0, 30)}
            {scm.message && scm.message.length > 30 && <span>…</span>}
          </span>
        )}
      </span>
    );
  };

  private selectedRenderer = (option: Option<string>) => {
    const execution = this.getExecutionFromId(option.value);
    return (
      <span>
        <ExecutionBuildTitle execution={execution} defaultToTimestamp={true} /> <strong>({execution.status})</strong>
      </span>
    );
  };

  public render() {
    const { executions, executionsLoading, loadError, selectedExecution } = this.state;

    const options = executions.map((execution) => {
      return {
        value: execution.id,
      };
    });

    return (
      <div className="form-group">
        <label className="col-md-4 sm-label-right">Execution</label>
        {executionsLoading && (
          <div className="col-md-6">
            <div className="form-control-static text-center">
              <Spinner size={'small'} />
            </div>
          </div>
        )}
        {loadError && <div className="col-md-6">Error loading executions!</div>}
        {!executionsLoading && (
          <div className="col-md-7">
            {executions.length === 0 && (
              <div>
                <p className="form-control-static">No recent executions found</p>
              </div>
            )}
            {executions.length > 0 && (
              <TetheredSelect
                options={options}
                optionRenderer={this.optionRenderer}
                clearable={false}
                value={selectedExecution}
                valueRenderer={this.selectedRenderer}
                onChange={this.handleExecutionChanged}
              />
            )}
          </div>
        )}
      </div>
    );
  }
}

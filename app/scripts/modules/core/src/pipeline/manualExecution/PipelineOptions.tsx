import { FormikProps } from 'formik';
import { head } from 'lodash';
import React from 'react';
import { Option } from 'react-select';
import { from as observableFrom, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { IPipelineTemplateConfig, PipelineTemplateReader, PipelineTemplateV2Service } from '../config/templates';
import { IParameter, IPipeline, IPipelineCommand, ITrigger } from '../../domain';
import { FormField, TetheredSelect } from '../../presentation';

export interface IPipelineOptionsProps {
  formatParameterConfig: (p: IParameter[]) => { [key: string]: any };
  formatPipeline: (p: IPipeline) => IPipeline;
  formatTriggers: (t: ITrigger[]) => ITrigger[];
  pipelineChanged: (p: IPipeline) => void;
  triggerChanged: (t: ITrigger) => void;
  updateTriggerOptions: (t: ITrigger[]) => void;
  pipelineOptions: IPipeline[];
  formik: FormikProps<IPipelineCommand>;
}

export interface IPipelineOptionsState {
  planError: boolean;
}

export class PipelineOptions extends React.Component<IPipelineOptionsProps, IPipelineOptionsState> {
  private destroy$ = new Subject();

  constructor(props: IPipelineOptionsProps) {
    super(props);
    this.state = { planError: false };
  }

  public componentWillUnmount() {
    this.destroy$.next();
  }

  private setPipelinePlan = (pipeline: IPipeline): void => {
    const isV1PipelineMissingStages =
      pipeline.type === 'templatedPipeline' && (pipeline.stages === undefined || pipeline.stages.length === 0);
    const isV2Pipeline = PipelineTemplateV2Service.isV2PipelineConfig(pipeline);
    const { formatPipeline } = this.props;
    if (isV1PipelineMissingStages || isV2Pipeline) {
      const pipelineTemplateConfig: IPipelineTemplateConfig = {
        type: 'templatedPipeline',
        ...pipeline,
      };
      observableFrom(PipelineTemplateReader.getPipelinePlan(pipelineTemplateConfig))
        .pipe(takeUntil(this.destroy$))
        .subscribe(
          (plan: IPipeline) => {
            this.setPipelineSelectedValues(isV2Pipeline ? formatPipeline(plan) : pipeline);
          },
          () => {
            this.setPipelineSelectedValues(pipeline);
            if (isV2Pipeline) {
              this.setState({ planError: true });
            }
          },
        );
    } else {
      this.setPipelineSelectedValues(pipeline);
    }
  };

  private pipelineSelected = (option: Option<string>) => {
    const { formatPipeline, pipelineOptions } = this.props;
    const pipelineId = option.value;
    const pipeline = formatPipeline(pipelineOptions.find((p) => p.id === pipelineId));
    this.setPipelinePlan(pipeline);
  };

  private setPipelineSelectedValues = (pipeline: IPipeline) => {
    const {
      formatParameterConfig,
      formatTriggers,
      formik,
      pipelineChanged,
      triggerChanged,
      updateTriggerOptions,
    } = this.props;
    const parameters = formatParameterConfig(pipeline.parameterConfig || []);
    const triggers: ITrigger[] = pipeline.triggers || [];
    const manualExecutionTriggers: ITrigger[] = formatTriggers(triggers);
    const manualTrigger = head(manualExecutionTriggers);
    // Inject the default value into the options list if it is absent
    updateTriggerOptions(manualExecutionTriggers);
    formik.setFieldValue('pipeline', pipeline);
    formik.setFieldValue('parameters', parameters);
    formik.setFieldValue('trigger', manualTrigger);
    pipelineChanged(pipeline);
    triggerChanged(manualTrigger);
  };

  public render() {
    const { formik, pipelineOptions } = this.props;
    if (this.state.planError) {
      return (
        <div className="form-group row">
          <div className="alert alert-danger">
            <p>Could not load pipeline plan. Please try reloading.</p>
          </div>
        </div>
      );
    } else {
      return (
        <div className="form-group row">
          <FormField
            label="Pipeline"
            onChange={this.pipelineSelected}
            value={formik.values.pipeline ? formik.values.pipeline.id : ''}
            input={(props) => (
              <TetheredSelect
                {...props}
                clearable={false}
                className="pipeline-select"
                options={pipelineOptions.map((p) => ({
                  label: p.name,
                  value: p.id,
                }))}
              />
            )}
          />
        </div>
      );
    }
  }
}

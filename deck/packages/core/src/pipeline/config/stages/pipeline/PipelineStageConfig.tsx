import { pickBy } from 'lodash';
import React from 'react';

import { ApplicationReader } from '../../../../application/service/ApplicationReader';
import type { IStageConfigProps } from '../common';
import { StageConfigField } from '../common';
import type { IPipeline } from '../../../../domain';
import { HelpField } from '../../../../help/HelpField';
import { ReactSelectInput } from '../../../../presentation';
import { PipelineConfigService } from '../../services/PipelineConfigService';
import { PipelineTemplateReader, PipelineTemplateV2Service } from '../../templates';

interface IPipelineParameterOption {
  value: string;
}

interface IPipelineParameterConfig {
  default: string;
  description?: string;
  hasOptions?: boolean;
  name: string;
  options?: IPipelineParameterOption[];
}

function hasSpel(value: string): boolean {
  return Boolean(value && value.includes('${'));
}

function isParameterExpression(value: any): boolean {
  return typeof value === 'string' && value.startsWith('${') && value.endsWith('}');
}

function normalizeParameters(parameterConfig: IPipelineParameterConfig[]): IPipelineParameterConfig[] {
  return parameterConfig.map((parameter) => {
    const options = parameter.options ? parameter.options.slice() : undefined;
    if (parameter.default && options && !options.some((option) => option.value === parameter.default)) {
      options.unshift({ value: parameter.default });
    }
    return { ...parameter, options };
  });
}

export function PipelineStageConfig({ application, pipeline, stage, updateStageField }: IStageConfigProps) {
  const [applications, setApplications] = React.useState<string[]>([]);
  const [pipelines, setPipelines] = React.useState<IPipeline[]>([]);
  const [pipelinesLoaded, setPipelinesLoaded] = React.useState(false);
  const [pipelineParameters, setPipelineParameters] = React.useState<IPipelineParameterConfig[]>([]);
  const [useDefaultParameters, setUseDefaultParameters] = React.useState<Record<string, boolean>>({});
  const [userSuppliedParameters, setUserSuppliedParameters] = React.useState<Record<string, string>>({});
  const [invalidParameters, setInvalidParameters] = React.useState<Record<string, string>>({});
  const parameterExpression = isParameterExpression(stage.pipelineParameters);

  React.useEffect(() => {
    const defaults: Record<string, any> = {};
    if (stage.failPipeline === undefined) {
      defaults.failPipeline = true;
    }
    if (stage.waitForCompletion === undefined) {
      defaults.waitForCompletion = true;
    }
    if (!stage.application) {
      defaults.application = application.name;
    }
    if (Object.keys(defaults).length) {
      updateStageField(defaults);
    }
  }, []);

  React.useEffect(() => {
    let active = true;
    ApplicationReader.listApplications().then((loadedApplications) => {
      if (active) {
        setApplications(loadedApplications.map((app) => app.name).sort());
      }
    });
    return () => {
      active = false;
    };
  }, []);

  React.useEffect(() => {
    let active = true;
    setPipelinesLoaded(false);

    if (!stage.application || hasSpel(stage.application)) {
      setPipelines([]);
      setPipelinesLoaded(true);
      return () => {
        active = false;
      };
    }

    PipelineConfigService.getPipelinesForApplication(stage.application).then((loadedPipelines) => {
      if (!active) {
        return;
      }
      const nextPipelines = loadedPipelines.filter((candidate) => candidate.id !== pipeline.id);
      setPipelines(nextPipelines);
      setPipelinesLoaded(true);

      if (
        stage.pipeline &&
        !hasSpel(stage.pipeline) &&
        !loadedPipelines.some((candidate) => candidate.id === stage.pipeline)
      ) {
        updateStageField({ pipeline: null });
      }
    });

    return () => {
      active = false;
    };
  }, [stage.application, pipeline.id]);

  React.useEffect(() => {
    if (!stage.pipeline || hasSpel(stage.pipeline) || hasSpel(stage.application)) {
      clearParams();
      return;
    }

    const selectedPipeline = pipelines.find((candidate) => candidate.id === stage.pipeline);
    if (!selectedPipeline) {
      clearParams();
      return;
    }

    if (PipelineTemplateV2Service.isV2PipelineConfig(selectedPipeline)) {
      PipelineTemplateReader.getPipelinePlan(selectedPipeline as any)
        .then((plan) => applyPipelineConfigParameters(plan as any))
        .catch(clearParams);
    } else {
      applyPipelineConfigParameters(selectedPipeline as any);
    }
  }, [stage.pipeline, stage.application, pipelines]);

  const clearParams = () => {
    setPipelineParameters([]);
    setUseDefaultParameters({});
    setUserSuppliedParameters({});
    setInvalidParameters({});
  };

  const applyPipelineConfigParameters = (config: IPipeline & { parameterConfig?: IPipelineParameterConfig[] }) => {
    if (!config?.parameterConfig?.length) {
      clearParams();
      return;
    }

    const normalizedParameters = normalizeParameters(config.parameterConfig);
    const nextParameters = parameterExpression ? {} : { ...(stage.pipelineParameters || {}) };
    const acceptedParameterNames = normalizedParameters.map((parameter) => parameter.name);
    const nextInvalidParameters = pickBy(nextParameters, (_value, name) => !acceptedParameterNames.includes(name));
    const nextUseDefaultParameters: Record<string, boolean> = {};

    normalizedParameters.forEach((parameter) => {
      if (!parameterExpression && !(parameter.name in nextParameters) && parameter.default !== null) {
        nextUseDefaultParameters[parameter.name] = true;
      }
    });

    if (!stage.pipelineParameters && !parameterExpression) {
      updateStageField({ pipelineParameters: nextParameters });
    }
    setPipelineParameters(normalizedParameters);
    setUseDefaultParameters(nextUseDefaultParameters);
    setUserSuppliedParameters(nextParameters);
    setInvalidParameters(nextInvalidParameters as Record<string, string>);
  };

  const updateParam = (parameter: string, value: string, useDefault: boolean) => {
    const nextParameters = { ...userSuppliedParameters };
    if (useDefault) {
      delete nextParameters[parameter];
    } else {
      nextParameters[parameter] = value || '';
    }
    setUserSuppliedParameters(nextParameters);
    updateStageField({ pipelineParameters: nextParameters });
  };

  const removeInvalidParameters = () => {
    const nextParameters = { ...userSuppliedParameters };
    Object.keys(invalidParameters).forEach((param) => delete nextParameters[param]);
    setUserSuppliedParameters(nextParameters);
    setInvalidParameters({});
    updateStageField({ pipelineParameters: nextParameters });
  };

  const sortedParameters = pipelineParameters.slice().sort((a, b) => a.name.localeCompare(b.name));
  const hasInvalidParameters = Boolean(Object.keys(invalidParameters).length && !parameterExpression);

  return (
    <div className="form-horizontal">
      <StageConfigField label="Application">
        {hasSpel(stage.application) ? (
          <input
            className="form-control input-sm"
            onChange={(event) => updateStageField({ application: event.target.value })}
            value={stage.application || ''}
          />
        ) : (
          <ReactSelectInput
            inputClassName="pipeline-stage-application-select"
            mode="VIRTUALIZED"
            name="application"
            onChange={(event) => updateStageField({ application: event.target.value })}
            placeholder="None"
            stringOptions={applications}
            value={stage.application || ''}
          />
        )}
      </StageConfigField>
      <StageConfigField label="Pipeline">
        {hasSpel(stage.pipeline) || hasSpel(stage.application) ? (
          <input
            className="form-control input-sm"
            onChange={(event) => updateStageField({ pipeline: event.target.value })}
            value={stage.pipeline || ''}
          />
        ) : (
          stage.application &&
          pipelinesLoaded && (
            <select
              className="form-control input-sm"
              onChange={(event) => updateStageField({ pipeline: event.target.value })}
              value={stage.pipeline || ''}
            >
              <option value="">Select a pipeline...</option>
              {pipelines
                .slice()
                .sort((a, b) => (a.name || '').localeCompare(b.name || ''))
                .map((candidate) => (
                  <option key={candidate.id} value={candidate.id}>
                    {candidate.name}
                  </option>
                ))}
            </select>
          )
        )}
      </StageConfigField>
      {Boolean(sortedParameters.length) && (
        <div className="well well-sm clearfix ng-scope col-md-offset-1 col-md-10">
          <h4 className="text-left">Pipeline Parameters</h4>
          {sortedParameters.map((parameter) => {
            const useDefault = Boolean(useDefaultParameters[parameter.name]);
            const parameterValue = userSuppliedParameters[parameter.name] || '';
            const hasOptionValue =
              !parameterValue || (parameter.options || []).some((option) => option.value === parameterValue);
            return (
              <div className="form-group" key={parameter.name}>
                <div className="col-md-3 sm-label-right">
                  <b>{parameter.name}</b> {parameter.description && <HelpField content={parameter.description} />}
                </div>
                <div className="col-md-5">
                  {useDefault ? (
                    <input className="form-control input-sm" disabled type="text" value={parameter.default || ''} />
                  ) : parameter.hasOptions && hasOptionValue ? (
                    <select
                      className="form-control input-sm"
                      onChange={(event) => updateParam(parameter.name, event.target.value, false)}
                      value={parameterValue}
                    >
                      <option value="" />
                      {(parameter.options || []).map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.value}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <input
                      className="form-control input-sm"
                      onChange={(event) => updateParam(parameter.name, event.target.value, false)}
                      type="text"
                      value={parameterValue}
                    />
                  )}
                </div>
                {parameter.default !== null && (
                  <div className="checkbox col-md-4">
                    <label>
                      <input
                        checked={useDefault}
                        onChange={(event) => {
                          setUseDefaultParameters({ ...useDefaultParameters, [parameter.name]: event.target.checked });
                          updateParam(parameter.name, userSuppliedParameters[parameter.name], event.target.checked);
                        }}
                        type="checkbox"
                      />
                      Use default
                    </label>
                  </div>
                )}
              </div>
            );
          })}
          {hasInvalidParameters && (
            <div className="horizontal center sp-margin-l-top" style={{ width: '100%' }}>
              <div className="alert alert-danger vertical">
                <p>
                  <i className="fa fa-exclamation-triangle" /> The following parameters are not accepted by the pipeline
                  but are still set in the stage configuration:
                </p>
                {Object.entries(invalidParameters).map(([paramName, paramValue]) => (
                  <div className="flex-container-h" key={paramName} style={{ margin: '0.5em' }}>
                    <label className="col-md-2">{paramName}</label>
                    <input className="flex-grow" disabled style={{ width: '100%' }} type="text" value={paramValue} />
                  </div>
                ))}
                <button className="self-right passive" onClick={removeInvalidParameters} type="button">
                  Remove all
                </button>
              </div>
            </div>
          )}
          {parameterExpression && (
            <div className="horizontal center sp-margin-l-top" style={{ width: '100%' }}>
              <div className="warning-text alert-info vertical">
                <p>
                  <i className="fa fa-exclamation-triangle" /> The pipelineParameters is defined as a SpeL expression
                  and will be fully evaluated on Runtime.
                </p>
                <p>
                  <code>{stage.pipelineParameters}</code>
                </p>
              </div>
            </div>
          )}
        </div>
      )}
      <StageConfigField label="Skip downstream output" helpKey="pipeline.skipDownstreamOutput">
        <input
          checked={Boolean(stage.skipDownstreamOutput)}
          className="input-sm"
          name="skipDownstreamOutput"
          onChange={(event) => updateStageField({ skipDownstreamOutput: event.target.checked })}
          type="checkbox"
        />
      </StageConfigField>
      <StageConfigField label="Wait for results" helpKey="pipeline.waitForCompletion">
        <input
          checked={Boolean(stage.waitForCompletion)}
          className="input-sm"
          name="waitForCompletion"
          onChange={(event) => updateStageField({ waitForCompletion: event.target.checked })}
          type="checkbox"
        />
      </StageConfigField>
    </div>
  );
}

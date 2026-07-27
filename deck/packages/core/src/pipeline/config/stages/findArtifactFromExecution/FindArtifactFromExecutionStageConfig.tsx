import React from 'react';

import { ApplicationReader } from '../../../../application/service/ApplicationReader';
import { ExpectedArtifactService } from '../../../../artifact/expectedArtifact.service';
import type { IStageConfigProps } from '../common';
import { StageConfigField } from '../common';
import type { IPipeline, IStage } from '../../../../domain';
import { HelpField } from '../../../../help';
import { PipelineConfigService } from '../../services/PipelineConfigService';

function getStageDefaults(stage: IStage): Partial<IStage> {
  const defaults: Partial<IStage> = {};

  if (!stage.executionOptions) {
    defaults.executionOptions = { successful: true };
  }

  if (!Array.isArray(stage.expectedArtifacts)) {
    const initialArtifact = ExpectedArtifactService.createEmptyArtifact();
    if (stage.expectedArtifact) {
      Object.assign(initialArtifact, stage.expectedArtifact);
    }
    defaults.expectedArtifacts = [initialArtifact];
  }

  return defaults;
}

export function FindArtifactFromExecutionStageConfig({ stage, updateStageField }: IStageConfigProps) {
  const [applications, setApplications] = React.useState<string[]>([]);
  const [applicationsLoaded, setApplicationsLoaded] = React.useState(false);
  const [pipelines, setPipelines] = React.useState<IPipeline[]>([]);
  const [pipelinesLoaded, setPipelinesLoaded] = React.useState(false);
  const executionOptions = stage.executionOptions || { successful: true };

  React.useEffect(() => {
    const defaults = getStageDefaults(stage);
    if (Object.keys(defaults).length) {
      updateStageField(defaults);
    }
  }, [stage.refId]);

  React.useEffect(() => {
    let active = true;
    ApplicationReader.listApplications().then((apps) => {
      if (!active) {
        return;
      }
      setApplications(apps.map((app) => app.name));
      setApplicationsLoaded(true);
    });
    return () => {
      active = false;
    };
  }, []);

  React.useEffect(() => {
    let active = true;
    setPipelinesLoaded(false);
    if (!stage.application) {
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
      setPipelines(loadedPipelines);
      setPipelinesLoaded(true);
    });

    return () => {
      active = false;
    };
  }, [stage.application]);

  const updateExecutionOption = (field: 'successful' | 'terminal' | 'running', value: boolean) => {
    updateStageField({ executionOptions: { ...executionOptions, [field]: value } });
  };

  return (
    <div className="form-horizontal clearfix">
      <h4>Pipeline Selector</h4>
      <StageConfigField label="Application">
        <select
          className="form-control input-sm"
          disabled={!applicationsLoaded}
          onChange={(event) => updateStageField({ application: event.target.value })}
          value={stage.application || ''}
        >
          <option value="">{applicationsLoaded ? 'Select an application...' : 'Loading applications...'}</option>
          {applications.map((application) => (
            <option key={application} value={application}>
              {application}
            </option>
          ))}
        </select>
      </StageConfigField>
      <StageConfigField label="Pipeline">
        <select
          className="form-control input-sm"
          disabled={!stage.application || !pipelinesLoaded}
          onChange={(event) => updateStageField({ pipeline: event.target.value })}
          value={stage.pipeline || ''}
        >
          <option value="">{pipelinesLoaded ? 'Select a pipeline...' : 'Loading pipelines...'}</option>
          {pipelines.map((pipeline) => (
            <option key={pipeline.id} value={pipeline.id}>
              {pipeline.name}
            </option>
          ))}
        </select>
      </StageConfigField>

      <h4>Execution Options</h4>
      <div className="form-group">
        <label className="col-md-2 col-md-offset-1 sm-label-right">
          Consider executions
          <HelpField id="pipeline.config.findArtifactFromExecution.considerExecutions" />
        </label>
        <div className="col-md-9">
          <div className="checkbox">
            <label>
              <input
                checked={!!executionOptions.successful}
                onChange={(event) => updateExecutionOption('successful', event.target.checked)}
                type="checkbox"
              />{' '}
              that are successful
            </label>
          </div>
          <div className="checkbox">
            <label>
              <input
                checked={!!executionOptions.terminal}
                onChange={(event) => updateExecutionOption('terminal', event.target.checked)}
                type="checkbox"
              />{' '}
              that are terminal (have failed)
            </label>
          </div>
          <div className="checkbox">
            <label>
              <input
                checked={!!executionOptions.running}
                onChange={(event) => updateExecutionOption('running', event.target.checked)}
                type="checkbox"
              />{' '}
              that are still running
            </label>
          </div>
        </div>
      </div>
    </div>
  );
}

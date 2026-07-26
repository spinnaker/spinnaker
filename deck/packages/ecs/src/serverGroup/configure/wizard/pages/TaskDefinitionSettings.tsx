import React from 'react';

import { HelpField } from '@spinnaker/core';

import type { IEcsWizardPageProps } from './common';
import { TaskDefinition } from '../taskDefinition/TaskDefinition';

export const TaskDefinitionSettings = ({ command, configureCommand, onFieldChange }: IEcsWizardPageProps) => (
  <div className="clearfix" data-test-id="EcsServerGroupWizard.taskDefinition">
    <div className="row">
      <div className="col-md-12">
        <div className="container-fluid form-horizontal">
          <div className="form-group">
            <div className="col-md-4 sm-label-right">
              <b>Task Definition source</b> <HelpField id="ecs.taskDefinition" />
            </div>
            <div className="col-md-2 radio">
              <label>
                <input
                  checked={!command.useTaskDefinitionArtifact}
                  data-test-id="ServerGroup.useInputs"
                  id="taskDefinitionSourceInputs"
                  onChange={() => onFieldChange('useTaskDefinitionArtifact', false)}
                  type="radio"
                />{' '}
                Inputs
              </label>
            </div>
            <div className="col-md-2 radio">
              <label>
                <input
                  checked={command.useTaskDefinitionArtifact}
                  data-test-id="ServerGroup.useArtifacts"
                  disabled={command.viewState.mode === 'create'}
                  id="taskDefinitionSourceArtifact"
                  onChange={() => onFieldChange('useTaskDefinitionArtifact', true)}
                  type="radio"
                />{' '}
                Artifact
              </label>
            </div>
          </div>
          {command.viewState.mode === 'create' && (
            <div className="color-text-caption">
              <hr />
              <em>Artifacts not supported outside of pipelines.</em>
            </div>
          )}
        </div>
      </div>
      {command.useTaskDefinitionArtifact && (
        <div className="col-md-12">
          <hr />
          <TaskDefinition command={command} configureCommand={configureCommand} notifyAngular={onFieldChange} />
        </div>
      )}
    </div>
  </div>
);

import { UISref } from '@uirouter/react';
import React, { useState } from 'react';

import type { IExecutionDetailsSectionProps } from '../common';
import { ExecutionDetailsSection } from '../common';
import { StageFailureMessage } from '../../../details';
import CancelAllModal from './modals/CancelAllModal';
import CancelModal from './modals/CancelModal';
import RollbackAllAppsModal from './modals/RollbackAllAppsModal';
import RollbackModal from './modals/RollbackModal';
import { Tooltip } from '../../../../presentation';
import { duration, timestamp } from '../../../../utils';

/*
 * You can use this component to provide information to users about
 * how the stage was configured and the results of its execution.
 */
export function RunMultiplePipelinesStageExecutionDetails(props: IExecutionDetailsSectionProps) {
  let runningExecutions: any[] = [];
  const executionsSet = new Set();

  const [executionData, setExecutionData] = useState({});
  const [modalOpen, setModalOpen] = useState(false);
  const [rollbackModalOpen, setRollbackModalOpen] = useState(false);
  const [rollbackAllAppsModalOpen, setRollbackAllAppsModalOpen] = useState(false);
  const [cancelAllModalOpen, setCancelAllModalOpen] = useState(false);

  if (props.stage.outputs.executionsList == undefined) {
    props.stage.outputs.executionsList = [];
  }

  const { application, stage } = props;

  const runningExecutionsDataSource = application.getDataSource('runningExecutions');
  if (runningExecutionsDataSource != undefined) {
    runningExecutions = runningExecutionsDataSource.data;
  }

  const handleCancelClick = (execution: any) => async () => {
    setExecutionData({
      ...execution,
    });
    await new Promise((f) => setTimeout(f, 200));
    setModalOpen(true);
  };

  const handleRollbackClick = (execution: any) => async () => {
    setExecutionData({
      ...execution,
    });
    await new Promise((f) => setTimeout(f, 200));
    setRollbackModalOpen(true);
  };

  const handleAllRollbacksClick = () => {
    setRollbackAllAppsModalOpen(true);
  };

  const handleCancelAllClick = () => {
    setCancelAllModalOpen(true);
  };

  runningExecutions.forEach((execution: any) => {
    if (execution.trigger.correlationId != undefined) {
      if (execution.trigger.correlationId.includes(props.stage.id)) {
        executionsSet.add(execution);
      }
    }
  });

  function findIfExecutionListCreatedArtifacts(executions: any) {
    for (const execution of executions) {
      if (execution.artifactCreated != undefined) {
        return true;
      }
    }
    return false;
  }

  function findIfIndividualExecutionCreatedArtifacts(execution: any) {
    if (execution.artifactCreated != undefined) {
      return true;
    }
    return false;
  }

  return (
    <ExecutionDetailsSection name={props.name} current={props.current}>
      <StageFailureMessage stage={stage} message={stage.failureMessage} />
      <table className="table" style={{ marginBottom: '0px' }}>
        <thead>
          <tr>
            <th>App</th>
            <th>Started</th>
            <th>Duration</th>
            <th>Status</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {props.stage.outputs.executionsList.length === 0 && executionsSet.size > 0 && (
            <>
              {Array.from(executionsSet).map((execution: any) => {
                return (
                  <tr className="ng-scope info" key={execution.id}>
                    {execution.name != 'rollbackOnFailure' && (
                      <td>
                        <UISref
                          key={execution.id}
                          to="home.applications.application.pipelines.executionDetails.execution"
                          params={{
                            application: execution.application,
                            executionId: execution.id,
                            executionParams: {
                              application: application.name,
                              executionId: execution.id,
                            },
                          }}
                          options={{
                            inherit: false,
                            reload: 'home.applications.application.pipelines.executionDetails',
                          }}
                        >
                          <a>{execution.trigger.parentExecution.trigger.executionIdentifier}</a>
                        </UISref>{' '}
                      </td>
                    )}
                    <td className="ng-binding">{timestamp(execution.startTime)}</td>
                    <td className="ng-binding">{duration(execution.runningTimeInMs)}</td>
                    <td>
                      <span className={'label label-default label-' + execution.status.toLowerCase()}>
                        {execution.status}
                      </span>
                    </td>
                    {execution.name != 'rollbackOnFailure' && (
                      <td>
                        <Tooltip value="Cancel execution">
                          <button className="link" onClick={handleCancelClick(execution)} data-testid="cancel-btn">
                            <i style={{ color: '#bb231e' }} className="far fa-times-circle" />
                          </button>
                        </Tooltip>
                      </td>
                    )}
                  </tr>
                );
              })}
            </>
          )}
          {props.stage.outputs.executionsList.map((execution: any) => {
            return (
              <tr className="ng-scope info" key={execution.id}>
                <td>
                  <UISref
                    key={execution.id}
                    to="home.applications.application.pipelines.executionDetails.execution"
                    params={{
                      application: application.name,
                      executionId: execution.id,
                      executionParams: { application: application.name, executionId: execution.id },
                    }}
                    options={{
                      inherit: false,
                      reload: 'home.applications.application.pipelines.executionDetails',
                    }}
                  >
                    <a>{execution.executionIdentifier}</a>
                  </UISref>{' '}
                </td>
                <td className="ng-binding">{timestamp(execution.startTime)}</td>
                <td className="ng-binding">{duration(execution.endTime - execution.startTime)}</td>
                <td>
                  <span className={'label label-default label-' + execution.status.toLowerCase()}>
                    {execution.status}
                  </span>
                </td>
                {findIfIndividualExecutionCreatedArtifacts(execution) && (
                  <td>
                    <Tooltip value="Rollback deploy">
                      <button className="link" onClick={handleRollbackClick(execution)} data-testid="rollback-btn">
                        <i className="glyphicon glyphicon-backward" />
                      </button>
                    </Tooltip>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
      {props.stage.outputs.executionsList.length === 0 && (
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button onClick={handleCancelAllClick}>
            <span className="far fa-times-circle visible-lg-inline"></span>
            <span className="far fa-times-circle visible-md-inline visible-sm-inline"></span>
            <span className="visible-lg-inline"> Cancel all executions </span>
          </button>
        </div>
      )}
      {props.stage.outputs.executionsList.length > 0 &&
        findIfExecutionListCreatedArtifacts(props.stage.outputs.executionsList) && (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button onClick={handleAllRollbacksClick}>
              <span className="glyphicon glyphicon-backward visible-lg-inline"></span>
              <span className="glyphicon glyphicon-backward visible-md-inline visible-sm-inline"></span>
              <span className="visible-lg-inline"> Rollback all apps </span>
            </button>
          </div>
        )}
      {modalOpen && <CancelModal setOpenModal={setModalOpen} executionData={executionData} application={application} />}
      {rollbackModalOpen && (
        <RollbackModal setOpenModal={setRollbackModalOpen} executionData={executionData} application={application} />
      )}
      {rollbackAllAppsModalOpen && (
        <RollbackAllAppsModal
          setOpenModal={setRollbackAllAppsModalOpen}
          allExecutions={props.stage.outputs.executionsList}
          application={application}
        />
      )}
      {cancelAllModalOpen && (
        <CancelAllModal setOpenModal={setCancelAllModalOpen} allRunning={executionsSet} application={application} />
      )}
    </ExecutionDetailsSection>
  );
}

// eslint-disable-next-line
export namespace RunMultiplePipelinesStageExecutionDetails {
  export const title = 'pipelineConfig';
}

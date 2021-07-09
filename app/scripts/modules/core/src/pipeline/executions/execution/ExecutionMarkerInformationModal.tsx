import { UISref } from '@uirouter/react';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { PipelineGraph } from '../../config/graph/PipelineGraph';
import { IExecution, IExecutionStageSummary } from '../../../domain';
import { ExecutionInformationService } from './executionInformation.service';
import { Spinner } from '../../../index';
import { ParametersAndArtifacts } from '../../status/ParametersAndArtifacts';
import { duration, relativeTime, timestamp } from '../../../utils';

import './executionMarkerInformationModal.less';

interface IExecutionErrorLocatorProps {
  executionId: string;
  stageId: string;
  onClose: Function;
}
interface IFailedStageExecutionLink {
  application: string;
  executionId: string;
  stageIndex: number;
}
interface IExecutionLocatorState {
  executionDetails: IExecution;
  failedInApplication: string;
  link: IFailedStageExecutionLink;
  showPipelineGraph: boolean;
  stageDetails: any;
}

export class ExecutionMarkerInformationModal extends React.PureComponent<
  IExecutionErrorLocatorProps,
  IExecutionLocatorState
> {
  allExecutions: any[];
  childExecution: any;
  childPipelineConfig: any;
  childTerminalPipelineStage: any;
  informationService: ExecutionInformationService;
  constructor(props: IExecutionErrorLocatorProps) {
    super(props);
    this.state = {
      executionDetails: null,
      failedInApplication: null,
      link: null,
      showPipelineGraph: false,
      stageDetails: null,
    };

    this.allExecutions = [];
    this.informationService = new ExecutionInformationService();
  }

  public componentDidMount() {
    this.getPipelineLink(this.props.stageId, this.props.executionId);
  }

  private getPipelineLink = async (stageId: string, executionId: string): Promise<void> => {
    // get the current execution id is from ExecutionMarker.tsx
    try {
      const currentExecution = await this.informationService.getExecution(executionId);
      let stageIndex;

      // get the current stage in the exeuction index is from ExecutionMarker.tsx
      const currentStage = currentExecution.stageSummaries.find((stage: IExecutionStageSummary, index: number) => {
        if (stage.id === stageId) {
          // store the index for our pipeline graph
          stageIndex = index;
          return stage;
        }

        return null;
      });

      // save this for rendering pipelines
      this.allExecutions.push({
        execution: currentExecution,
        stageId,
        stageIndex,
      });

      // get the child execution aka clicking View Pipeline Details
      const childExecution = await this.informationService.getExecution(currentStage.masterStage.context.executionId);
      const childTerminalStage = childExecution.stageSummaries.find((stage: IExecutionStageSummary, index: number) => {
        if (stage.status.toLocaleLowerCase() === 'terminal') stageIndex = index;

        return stage.status.toLowerCase() === 'terminal';
      });
      const childTerminalPipelineStage = childExecution.stageSummaries.find(
        (stage: IExecutionStageSummary) => stage.status.toLowerCase() === 'terminal' && stage.type === 'pipeline',
      );
      // get the current configuration for this execution
      const childPipelineConfig = await this.informationService.getPipelineConfig(
        childExecution.application,
        childExecution.pipelineConfigId,
      );

      if (childExecution && !childTerminalPipelineStage) {
        this.childExecution = childExecution;
        this.childPipelineConfig = childPipelineConfig;

        // save this for rendering pipelines
        this.allExecutions.push({
          execution: childExecution,
          stageId,
          stageIndex,
        });
      }

      if (childTerminalPipelineStage) {
        this.getPipelineLink(childTerminalPipelineStage.id, childExecution.id);
        this.childTerminalPipelineStage = childTerminalPipelineStage;
      } else {
        // now that we are complete let's fix up the allExecutions array
        // we are using allExecutions as a breadcrumb so reverse them then pop the first one since there user is already at the first one
        this.allExecutions.reverse();
        this.allExecutions.pop();

        this.setState({
          executionDetails: this.childExecution || currentExecution,
          failedInApplication: currentStage.masterStage.context.application,
          link: {
            application: currentStage.masterStage.context.application,
            executionId: currentStage.masterStage.context.executionId,
            stageIndex: this.allExecutions[0].stageIndex,
          },
          stageDetails: childTerminalStage || this.childTerminalPipelineStage || currentStage,
        });
      }
    } catch (err) {
      if (console) {
        console.error('Error retrieving pipeline execution data.');
        this.props.onClose();
      }
    }
  };

  private showPipelineGraph = () => {
    this.setState({ showPipelineGraph: true });
  };

  private hidePipelineGraph = () => {
    this.setState({ showPipelineGraph: false });
  };

  public render(): React.ReactElement<HTMLDivElement> {
    const { executionDetails, failedInApplication, link, stageDetails } = this.state;

    const content = this.state.showPipelineGraph ? (
      <div className="">
        {this.allExecutions.map((item) => {
          return (
            <div key={`${item.execution.id}-${item.execution.name}`} className="execution-graph">
              {item.execution.application} - {item.execution.name}
              <PipelineGraph
                execution={item.execution}
                onNodeClick={() => {}}
                viewState={{
                  activeStageId: item.stageIndex,
                  activeSubStageId: null,
                  canTriggerPipelineManually: false,
                  canConfigure: false,
                }}
              />
            </div>
          );
        })}
      </div>
    ) : (
      <div>
        {!executionDetails ? (
          <div>
            <Spinner size="medium" />
          </div>
        ) : (
          <div className="information-details">
            <h5 className="pipeline-name">{executionDetails.name}</h5>
            <div style={{ fontWeight: 'bold' }}>PIPELINE</div>
            <div className="bottom-margin">
              <div>{`[${executionDetails.authentication.user}] ${
                executionDetails.user ? `(${executionDetails.user})` : ''
              }`}</div>
              <div>{relativeTime(executionDetails.startTime || executionDetails.buildTime)}</div>
              <div>
                Status: <span className="status">{executionDetails.status}</span> by parent pipeline{' '}
                {timestamp(executionDetails.trigger.parentExecution.buildTime)}
              </div>
            </div>
            <div className="bottom-margin parameters">
              <ParametersAndArtifacts
                execution={executionDetails}
                expandParamsOnInit={false}
                pipelineConfig={this.childPipelineConfig}
              />
            </div>
            {stageDetails && (
              <div className="bottom-margin information-stage-details">
                <div>STAGE DETAILS</div>
                <div>Name: {stageDetails.name}</div>
                <div>Duration: {duration(stageDetails.endTime - stageDetails.startTime)}</div>
                <div>Exception: {stageDetails.getErrorMessage || 'No message available'}</div>
                <div>
                  Pipeline Execution History <span className="information-history-note">(Decending order)</span>
                  <table className="information-pipeline-execution-history">
                    <thead>
                      <tr>
                        <th></th>
                        <th>APPLICATION</th>
                        <th>PIPELINE NAME</th>
                        <th>STAGE</th>
                        <th>STATUS</th>
                        <th>DURATION</th>
                      </tr>
                    </thead>
                    <tbody className="information-section">
                      {this.allExecutions.map((item: any, index: number) => {
                        return (
                          <tr key={`execution-history-for-${item.execution.id}-${item.stageId}`}>
                            <td>{index === 0 && <i className="fa fa-circle"></i>}</td>
                            <td className="information-app">
                              <UISref
                                to="home.applications.application.pipelines.executionDetails.execution"
                                params={{
                                  application: item.execution.application,
                                  executionId: item.execution.id,
                                  executionParams: {
                                    application: item.execution.application,
                                    executionId: item.execution.id,
                                    stage: item.stageId,
                                  },
                                }}
                                options={{
                                  inherit: false,
                                  reload: 'home.applications.application.pipelines.executionDetails',
                                }}
                              >
                                <a id={`stage-${item.stageId}`} target="_blank">
                                  {item.execution.application}
                                </a>
                              </UISref>
                            </td>
                            <td className="information-execution">
                              <UISref
                                to="home.applications.application.pipelines.executionDetails.execution"
                                params={{
                                  application: item.execution.application,
                                  executionId: item.execution.id,
                                  executionParams: {
                                    application: item.execution.application,
                                    executionId: item.execution.id,
                                    stage: item.stageId,
                                  },
                                }}
                                options={{
                                  inherit: false,
                                  reload: 'home.applications.application.pipelines.executionDetails',
                                }}
                              >
                                <a id={`stage-${item.stageId}`} target="_blank">
                                  {item.execution.name}
                                </a>
                              </UISref>
                            </td>
                            <td>
                              <UISref
                                to="home.applications.application.pipelines.executionDetails.execution"
                                params={{
                                  application: item.execution.application,
                                  executionId: item.execution.id,
                                  executionParams: {
                                    application: item.execution.application,
                                    executionId: item.execution.id,
                                    stage: item.stageId,
                                  },
                                }}
                                options={{
                                  inherit: false,
                                  reload: 'home.applications.application.pipelines.executionDetails',
                                }}
                              >
                                <a id={`stage-${item.stageId}`} target="_blank">
                                  {item.execution.stageSummaries[item.stageIndex].name}
                                </a>
                              </UISref>
                            </td>
                            <td className="information-stage-status">
                              <span className="information-terminal-stage color-white">
                                <UISref
                                  to="home.applications.application.pipelines.executionDetails.execution"
                                  params={{
                                    application: item.execution.application,
                                    executionId: item.execution.id,
                                    executionParams: {
                                      application: item.execution.application,
                                      executionId: item.execution.id,
                                      stage: item.stageId,
                                    },
                                  }}
                                  options={{
                                    inherit: false,
                                    reload: 'home.applications.application.pipelines.executionDetails',
                                  }}
                                >
                                  <a id={`stage-${item.stageId}`} target="_blank">
                                    {item.execution.stageSummaries[item.stageIndex].status}
                                  </a>
                                </UISref>
                              </span>
                            </td>
                            <td>{duration(item.execution.endTime - item.execution.startTime)}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
        {link && (
          <div className="execution-marker-information-popver-footer">
            <div>
              <button className="btn btn-link graph-all-buttons" aria-disabled="false" onClick={this.showPipelineGraph}>
                Show Pipeline Execution History Graphs
              </button>
            </div>
            <div className="graph-all-buttons">
              <UISref
                to="home.applications.application.pipelines.executionDetails.execution"
                params={{
                  application: link.application,
                  executionId: link.executionId,
                  executionParams: {
                    application: link.application,
                    executionId: link.executionId,
                    stage: link.stageIndex,
                  },
                }}
                options={{ inherit: false, reload: 'home.applications.application.pipelines.executionDetails' }}
              >
                <a target="_self">Link to the Last Failed Execution Stage</a>
              </UISref>
            </div>
          </div>
        )}
      </div>
    );
    const title = this.state.showPipelineGraph ? (
      <span>
        <span className="clickable information-back">
          <i
            className="fa fa-chevron-left"
            onClick={() => {
              this.hidePipelineGraph();
            }}
          ></i>
        </span>
        Pipeline Execution History Graphs <span className="information-history-note">(Decending order)</span>
      </span>
    ) : (
      <span>{failedInApplication ? failedInApplication : '-'}</span>
    );

    return (
      <Modal show onHide={this.props.onClose} className="execution-marker-information-popover" backdrop="static">
        <Modal.Header closeButton={true}>
          <Modal.Title>{title}</Modal.Title>
        </Modal.Header>
        <Modal.Body>{content}</Modal.Body>
      </Modal>
    );
  }
}

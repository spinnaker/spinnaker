import { isEqual, keyBy } from 'lodash';
import React from 'react';
import { Option } from 'react-select';

import { IExecution, IPipeline, IStage } from '../../../../domain';
import { FormField, IStageForSpelPreview, ReactSelectInput, useData } from '../../../../presentation';
import { ReactInjector } from '../../../../reactShims';
import { ExecutionStatus } from '../../../status/ExecutionStatus';
import { relativeTime, timestamp } from '../../../../utils';

export interface IExecutionAndStagePickerProps {
  pipeline: IPipeline;
  pipelineStage: IStage;
  onChange(chosenExecutionAndStage: IStageForSpelPreview): void;
}

export function ExecutionAndStagePicker(props: IExecutionAndStagePickerProps) {
  const { pipeline, pipelineStage, onChange } = props;
  const { executionService } = ReactInjector;
  const [execution, setExecution] = React.useState<IExecution>(null);
  const [stageId, setStageId] = React.useState<string>(null);
  const [showStageSelector, setShowStageSelector] = React.useState(false);

  const fetchExecutions = useData(
    () => executionService.getExecutionsForConfigIds([pipeline.id], { limit: 100 }),
    [],
    [],
  );
  const executions = fetchExecutions.result;
  const stages = execution && execution.stages;

  // When executions load, select the first one.
  React.useEffect(() => executions && setExecution(executions[0]), [executions]);

  // When an execution is chosen or the current pipeline or pipeline stage changes,
  // find the matching stage from the execution
  React.useEffect(() => {
    const exactStage = findExactStageFromExecution(pipeline, execution, pipelineStage);
    setShowStageSelector(!!exactStage);
    setStageId(exactStage || findCloseStageFromExecution(pipeline, execution, pipelineStage));
  }, [pipeline, pipelineStage, execution]);

  // When the chosen execution or stage changes, notify the parent
  React.useEffect(() => {
    onChange({ executionLabel: executionLabel(execution), executionId: execution && execution.id, stageId });
  }, [execution, stageId]);

  const stageOptions: Option[] = stages ? stages.map((x) => ({ label: x.name, value: x.id })) : [];
  const executionOptions: Array<Option<IExecution>> = executions ? executions.map((value) => ({ value })) : [];

  if (fetchExecutions.status === 'RESOLVED' && !executions.length) {
    return (
      <p>
        This pipeline has never been executed. If you run this pipeline at least once, Spinnaker will show previews of
        the variables on this screen.
      </p>
    );
  }

  return (
    <>
      <p>
        Select a previous execution of this pipeline. Spinnaker will then evaluate each variable as if it had been part
        of that execution, and generate a preview for you to debug against.
      </p>

      <FormField
        label="Execution"
        value={execution}
        onChange={(e) => setExecution(e.target.value)}
        input={(inputProps) => (
          <ReactSelectInput<IExecution>
            {...inputProps}
            options={executionOptions as any}
            clearable={false}
            isLoading={fetchExecutions.status === 'PENDING'}
            optionRenderer={(option) => (
              <ExecutionStatus
                execution={(option.value as any) as IExecution}
                showingDetails={true}
                standalone={true}
              />
            )}
            valueRenderer={(value) => <span>{executionLabel(value as any)}</span>}
          />
        )}
      />

      {!showStageSelector && (
        <>
          <p className="sp-margin-m-top">
            Spinnaker could not find this exact same Evaluate Variables stage in the previous execution. Select a stage
            from the previous execution to use as a stand-in.
          </p>
          <FormField
            label="Stage"
            value={stageId}
            onChange={(e) => setStageId(e.target.value)}
            input={(inputProps) => (
              <ReactSelectInput {...inputProps} options={stageOptions} clearable={false} isLoading={!stages} />
            )}
          />
        </>
      )}
    </>
  );
}

function executionLabel(execution: IExecution) {
  if (!execution) {
    return null;
  }
  const time = execution.buildTime || execution.startTime;
  return `${timestamp(time)} (${relativeTime(time)})`;
}

function findExactStageFromExecution(pipeline: IPipeline, execution: IExecution, editStage: IStage): string {
  if (!pipeline || !execution || !editStage) {
    return null;
  }

  const pipelineStagesById = keyBy(pipeline.stages, 'refId');
  const executionStagesById = keyBy(execution.stages, 'refId');

  const exactMatch = execution.stages.find((s) => s.refId === editStage.refId && s.type === editStage.type);

  const pipelineRequisiteGraph = stageRequisiteStageGraph(editStage, pipelineStagesById);
  const executionRequisiteGraph = stageRequisiteStageGraph(editStage, executionStagesById);
  const exactGraphMatch = isEqual(pipelineRequisiteGraph, executionRequisiteGraph);

  return exactMatch && exactGraphMatch ? exactMatch.id : null;
}

function findCloseStageFromExecution(pipeline: IPipeline, execution: IExecution, editStage: IStage): string {
  if (!pipeline || !execution || !editStage) {
    return null;
  }
  const pipelineStagesById = keyBy(pipeline.stages, 'refId');
  const pipelineRequisiteGraph = stageRequisiteStageGraph(editStage, pipelineStagesById);

  const upstreamStages = Object.keys(pipelineRequisiteGraph).map((id) => pipelineStagesById[id]);
  return upstreamStages.map((stage) => findExactStageFromExecution(pipeline, execution, stage)).find((x) => !!x);
}

function stageRequisiteStageGraph(stage: IStage, allStages: { [key: string]: IStage }): { [key: string]: any } {
  return stage.requisiteStageRefIds
    .filter((ref) => /^[0-9]+$/.exec(ref.toString()))
    .reduce((acc, requisiteStageId) => {
      const requisiteStage = allStages[requisiteStageId];
      if (!requisiteStage) {
        return acc;
      }
      return { ...acc, [requisiteStageId]: stageRequisiteStageGraph(requisiteStage, allStages) };
    }, {});
}

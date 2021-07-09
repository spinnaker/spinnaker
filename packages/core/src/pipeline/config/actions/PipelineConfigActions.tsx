import React from 'react';
import { Dropdown } from 'react-bootstrap';

import { PipelineConfigAction } from './PipelineConfigAction';
import { IPipeline } from '../../../domain';

export interface IPipelineConfigActionsProps {
  pipeline: IPipeline;
  renamePipeline: () => void;
  deletePipeline: () => void;
  enablePipeline: () => void;
  disablePipeline: () => void;
  lockPipeline: () => void;
  unlockPipeline: () => void;
  editPipelineJson: () => void;
  showHistory: () => void;
  exportPipelineTemplate: () => void;
}

export function PipelineConfigActions(props: IPipelineConfigActionsProps) {
  const {
    pipeline,
    renamePipeline,
    deletePipeline,
    enablePipeline,
    disablePipeline,
    lockPipeline,
    unlockPipeline,
    editPipelineJson,
    showHistory,
    exportPipelineTemplate,
  } = props;

  return (
    <Dropdown className="dropdown" id="pipeline-actions-dropdown">
      <Dropdown.Toggle className="btn btn-sm dropdown-toggle">
        {pipeline.strategy === true ? 'Strategy' : 'Pipeline'} Actions
      </Dropdown.Toggle>
      <Dropdown.Menu className="dropdown-menu">
        {!pipeline.locked && <PipelineConfigAction name="Rename" action={renamePipeline} />}
        {!pipeline.locked && <PipelineConfigAction name="Delete" action={deletePipeline} />}
        {!pipeline.locked && pipeline.disabled && <PipelineConfigAction name="Enable" action={enablePipeline} />}
        {!pipeline.locked && !pipeline.disabled && <PipelineConfigAction name="Disable" action={disablePipeline} />}
        {!pipeline.locked && <PipelineConfigAction name="Lock" action={lockPipeline} />}
        {pipeline.locked && pipeline.locked.allowUnlockUi && (
          <PipelineConfigAction name="Unlock" action={unlockPipeline} />
        )}
        {pipeline.locked && <PipelineConfigAction name="Show JSON" action={editPipelineJson} />}
        {!pipeline.locked && <PipelineConfigAction name="Edit as JSON" action={editPipelineJson} />}
        <PipelineConfigAction name="Show Revision History" action={showHistory} />
        <PipelineConfigAction name="Export as Pipeline Template" action={exportPipelineTemplate} />
      </Dropdown.Menu>
    </Dropdown>
  );
}

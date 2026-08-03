import { UISref } from '@uirouter/react';
import * as React from 'react';

import { EntitySourcePopover } from './EntitySourcePopover';
import { useDeckRuntimeServices } from '../bootstrap/DeckRuntimeContext';
import type { ICreationMetadataTag, IExecution } from '../domain';
import { HoverablePopover, useData } from '../presentation';

export interface IEntitySourceProps {
  metadata: ICreationMetadataTag;
  relativePath?: string;
}

export const EntitySource = ({ metadata, relativePath = '^.^.^' }: IEntitySourceProps) => {
  const { executionService } = useDeckRuntimeServices();
  const executionType = metadata?.value?.executionType === 'pipeline' ? 'pipeline' : 'task';
  const { application, comments, executionId, stageId } = metadata?.value || {};

  const fetchExecution = () => {
    if (executionType === 'pipeline') {
      return executionService.getExecution(metadata?.value?.executionId);
    }

    return new Promise(() => {});
  };

  const { result: execution, status, error } = useData(fetchExecution, undefined, [
    executionType,
    metadata?.value?.executionId,
  ]);
  const isLoading = status !== 'RESOLVED';

  const srefPath =
    executionType === 'pipeline'
      ? `${relativePath}.pipelines.executionDetails.execution`
      : `${relativePath}.tasks.taskDetails`;
  const srefParams =
    executionType === 'pipeline' ? { application, executionId, stageId } : { application, taskId: executionId };

  const PopoverContent = () => (
    <EntitySourcePopover
      comments={comments}
      execution={execution as IExecution}
      executionType={executionType}
      metadata={metadata.value}
    />
  );

  if (!metadata || (executionType === 'pipeline' && isLoading)) {
    return null;
  }

  return (
    <div>
      <dt>Source</dt>
      <dd>
        <HoverablePopover Component={PopoverContent}>
          {error && <span>pipeline (not found)</span>}
          {!error && (
            <UISref to={srefPath} params={srefParams}>
              <a className="clickable">{executionType}</a>
            </UISref>
          )}
        </HoverablePopover>
      </dd>
    </div>
  );
};

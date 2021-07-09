import { UISref } from '@uirouter/react';
import { UIRouterContextComponent } from '@uirouter/react-hybrid';
import * as React from 'react';

import { EntitySourcePopover } from './EntitySourcePopover';
import { ICreationMetadataTag, IExecution } from '../domain';
import { HoverablePopover, useData } from '../presentation';
import { ReactInjector } from '../reactShims';

export interface IEntitySourceProps {
  metadata: ICreationMetadataTag;
  relativePath?: string;
}

export const EntitySource = ({ metadata, relativePath = '^.^.^' }: IEntitySourceProps) => {
  const executionType = metadata?.value?.executionType === 'pipeline' ? 'pipeline' : 'task';
  const { application, comments, executionId, stageId } = metadata?.value || {};

  const fetchExecution = () => {
    if (executionType === 'pipeline') {
      return ReactInjector.executionService.getExecution(metadata?.value?.executionId);
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
            <UIRouterContextComponent>
              <UISref to={srefPath} params={srefParams}>
                <a className="clickable">{executionType}</a>
              </UISref>
            </UIRouterContextComponent>
          )}
        </HoverablePopover>
      </dd>
    </div>
  );
};

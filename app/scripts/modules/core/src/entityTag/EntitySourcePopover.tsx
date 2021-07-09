import { capitalize } from 'lodash';
import * as React from 'react';

import { ICreationMetadata, IExecution } from '../domain';
import { LabeledValue, LabeledValueList, Markdown } from '../presentation';

interface IEntitySourcePopoverProps {
  comments: string;
  execution: IExecution;
  executionType: string;
  metadata: ICreationMetadata;
}

export const EntitySourcePopover = ({ comments, execution, executionType, metadata }: IEntitySourcePopoverProps) => {
  const { description, previousServerGroup, user } = metadata || {};
  return (
    <LabeledValueList>
      <LabeledValue label={capitalize(executionType)} value={description} />
      {execution && <LabeledValue label="Triggered By" value={user || execution.trigger.type} />}
      {!execution && user && <LabeledValue label="User" value={user} />}
      {previousServerGroup && <LabeledValue label="Replaced" value={previousServerGroup.name} />}
      {previousServerGroup && (
        <dd>{`${previousServerGroup.imageName}${
          previousServerGroup.imageId !== previousServerGroup.imageName ? `${previousServerGroup.imageId}` : ''
        }`}</dd>
      )}
      {comments && <LabeledValue label="Comments" value={<Markdown message={comments} />} />}
    </LabeledValueList>
  );
};

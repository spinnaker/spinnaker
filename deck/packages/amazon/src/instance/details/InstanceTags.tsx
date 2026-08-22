import { orderBy } from 'lodash';
import React from 'react';

import { CollapsibleSection, LabeledValue } from '@spinnaker/core';

export interface IInstanceTagsProps {
  tags: Array<{
    key: string;
    value: string;
  }>;
}

export const InstanceTags = ({ tags }: IInstanceTagsProps) => {
  const instanceTags = tags || [];
  const sortedTags = orderBy(instanceTags, ['key'], ['asc']);
  return (
    <CollapsibleSection heading="Tags" defaultExpanded={true}>
      {!instanceTags.length && <div>No tags associated with this server</div>}
      {Boolean(instanceTags.length) &&
        sortedTags.map((tag) => <LabeledValue key={tag.key} label={tag.key} value={tag.value} />)}
    </CollapsibleSection>
  );
};

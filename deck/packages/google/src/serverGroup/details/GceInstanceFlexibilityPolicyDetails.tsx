import { isEmpty } from 'lodash';
import React from 'react';

import type { IGceInstanceFlexibilityPolicy } from '../../domain/serverGroup';

export interface IGceInstanceFlexibilityPolicyDetailsProps {
  instanceFlexibilityPolicy?: IGceInstanceFlexibilityPolicy;
}

export function GceInstanceFlexibilityPolicyDetails({
  instanceFlexibilityPolicy,
}: IGceInstanceFlexibilityPolicyDetailsProps) {
  const selections = instanceFlexibilityPolicy?.instanceSelections;
  if (isEmpty(selections)) {
    return null;
  }

  return (
    <dl className="dl-horizontal dl-narrow">
      {Object.entries(selections).map(([name, selection]) => (
        <React.Fragment key={name}>
          <dt>Selection</dt>
          <dd>{name}</dd>
          {selection.rank != null && (
            <>
              <dt>Rank</dt>
              <dd>{selection.rank}</dd>
            </>
          )}
          <dt>Machine types</dt>
          <dd>{(selection.machineTypes || []).join(', ')}</dd>
        </React.Fragment>
      ))}
    </dl>
  );
}

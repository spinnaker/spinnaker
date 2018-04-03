import * as React from 'react';

import { HelpField } from '@spinnaker/core';

interface IOwnedByProps {
  owningApplications: string[];
  currentApplication: string;
}

const buildHelpText = (ownedBy: string[]): string => {
  if (ownedBy.length === 0) {
    return null;
  } else if (ownedBy.length === 1) {
    return `This config can only be edited from within the application <strong>${ownedBy[0]}.</strong>`;
  } else {
    return `This config can only be edited from within the following applications: <strong>${ownedBy.join(', ')}.</strong>`;
  }
};

export const OwnedBy = ({ owningApplications, currentApplication }: IOwnedByProps) => {
  if (owningApplications.length < 2 && owningApplications.includes(currentApplication)) {
    return null;
  }

  return (
    <span>
      Owned by: {owningApplications.join(', ')}
      &nbsp;
      <HelpField placement="right" content={buildHelpText(owningApplications)} />
    </span>
  );
};

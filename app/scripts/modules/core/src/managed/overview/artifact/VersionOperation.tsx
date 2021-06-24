import React from 'react';

import { QueryArtifactVersionTaskStatus, QueryConstraint } from '../types';

type AllStatuses = QueryConstraint['status'] | QueryArtifactVersionTaskStatus;
export const ACTION_DISPLAY_NAMES = ['passed', 'overridden', 'pending', 'failed'] as const;
export type ActionDisplayName = typeof ACTION_DISPLAY_NAMES[number];

const DEFAULT_ICON_CLASSNAME = 'far fa-hourglass md-icon-pending';

type ActionStatusUtils = {
  [key in AllStatuses]: { className?: string; displayName: ActionDisplayName };
};

// This ensures that we cover all of the options in AllStatuses (e.g. it will complain if BLOCKED is missing)
const actionStatusUtilsInternal: ActionStatusUtils = {
  FAIL: { className: 'fas fa-times md-icon-fail', displayName: 'failed' },
  FORCE_PASS: { className: 'fas fa-check md-icon-success', displayName: 'overridden' },
  PASS: { className: 'fas fa-check md-icon-success', displayName: 'passed' },
  PENDING: { displayName: 'pending' },
  NOT_EVALUATED: { displayName: 'pending' },
  BLOCKED: { displayName: 'pending' },
};

export const getActionStatusData = (status: AllStatuses): ActionStatusUtils[keyof ActionStatusUtils] | undefined => {
  const data = actionStatusUtilsInternal[status];
  if (!data) {
    console.error(`Missing data for action status ${status}`);
  }
  return data;
};

interface IVersionOperationIconProps {
  status: AllStatuses;
}

export const VersionOperationIcon = ({ status }: IVersionOperationIconProps) => {
  return <i className={getActionStatusData(status)?.className || DEFAULT_ICON_CLASSNAME} />;
};

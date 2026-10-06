export interface IManualJudgmentAuthorization {
  applicationRoles: Record<string, string[]>;
  currentUser?: string;
  preventSelfApproval: boolean;
  stageRoles?: string[];
  triggeredBy?: string;
  userRoles: string[];
}

export function isManualJudgmentStageNotAuthorized({
  applicationRoles,
  currentUser,
  preventSelfApproval,
  stageRoles,
  triggeredBy,
  userRoles,
}: IManualJudgmentAuthorization): boolean {
  if (!stageRoles?.length) {
    return false;
  }
  if (preventSelfApproval && (!triggeredBy || !currentUser || triggeredBy === currentUser)) {
    return true;
  }
  const permittedRoles = [
    ...(applicationRoles.WRITE || []),
    ...(applicationRoles.EXECUTE || []),
    ...(applicationRoles.CREATE || []),
  ];
  return !userRoles.some((role) => stageRoles.includes(role) && permittedRoles.includes(role));
}

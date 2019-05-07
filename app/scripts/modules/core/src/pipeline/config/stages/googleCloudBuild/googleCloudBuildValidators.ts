import { buildValidators, IContextualValidator, IStage } from '@spinnaker/core';

export const validate: IContextualValidator = (stage: IStage) => {
  const validation = buildValidators(stage);
  validation.field('account', 'Account').required();
  return validation.result();
};

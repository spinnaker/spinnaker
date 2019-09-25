import { FormValidator, IContextualValidator, IStage } from '@spinnaker/core';

export const validate: IContextualValidator = (stage: IStage) => {
  const formValidator = new FormValidator(stage);
  formValidator.field('account', 'Account').required();
  return formValidator.validateForm();
};

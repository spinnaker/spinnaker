import { FormValidator, IContextualValidator, IStage } from '@spinnaker/core';

export const validate: IContextualValidator = (stage: IStage) => {
  const formValidator = new FormValidator(stage);
  formValidator.field('credentials', 'Account').required();
  formValidator.field('regions', 'Regions').required();
  formValidator.field('cluster', 'Cluster').required();
  return formValidator.validateForm();
};

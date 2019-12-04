import { FormValidator, IContextualValidator, IStage } from '@spinnaker/core';
import { buildDefinitionSources } from './GoogleCloudBuildStageForm';

export const validate: IContextualValidator = (stage: IStage) => {
  const formValidator = new FormValidator(stage);
  formValidator.field('account', 'Account').required();
  if (stage.buildDefinitionSource === buildDefinitionSources.TRIGGER) {
    formValidator.field('triggerId', 'Trigger Name').required();
    formValidator.field('triggerType', 'Trigger Type').required();
    const triggerType = stage.triggerType;
    formValidator.field(`repoSource.${triggerType}`, 'Value').required();
  }
  return formValidator.validateForm();
};

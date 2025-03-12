import type { IContextualValidator } from '../FormikStageConfig';
import { BuildDefinitionSource } from './IGoogleCloudBuildStage';
import type { IStage } from '../../../../domain';
import { FormValidator } from '../../../../presentation';

export const validate: IContextualValidator = (stage: IStage) => {
  const formValidator = new FormValidator(stage);
  formValidator.field('account', 'Account').required();
  if (stage.buildDefinitionSource === BuildDefinitionSource.TEXT) {
    formValidator.field('buildDefinition', 'Build Definition').required();
  }
  if (stage.buildDefinitionSource === BuildDefinitionSource.TRIGGER) {
    formValidator.field('triggerId', 'Trigger Name').required();
    formValidator.field('triggerType', 'Trigger Type').required();
    const triggerType = stage.triggerType;
    formValidator.field(`repoSource.${triggerType}`, 'Value').required();
  }
  // todo(mneterval): add reusable validator for stage artifacts
  return formValidator.validateForm();
};

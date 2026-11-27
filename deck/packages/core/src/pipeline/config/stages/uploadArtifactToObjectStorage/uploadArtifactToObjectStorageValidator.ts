import type { IContextualValidator } from '../FormikStageConfig';
import type { IStage } from '../../../../domain';
import { FormValidator } from '../../../../presentation';

export const validateUploadArtifactToObjectStorageStage: IContextualValidator = (stage: IStage) => {
  const formValidator = new FormValidator(stage);

  formValidator.field('cloudProvider', 'Cloud Provider').required();
  formValidator.field('credentials', 'Account').required();
  formValidator.field('region', 'Region').required();
  formValidator.field('bucket', 'Bucket').required();
  formValidator.field('objectKeyPrefix', 'Object Key Prefix').required();

  return formValidator.validateForm();
};

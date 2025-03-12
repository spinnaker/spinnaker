import { KubernetesApplicationNameValidator } from './applicationName.validator';

describe('ApplicationNameValidator', () => {
  const validator = new KubernetesApplicationNameValidator();
  describe('validateSpecialCharacters', () => {
    it('Returns no warnings or errors for names that contain only alphanumeric characters', () => {
      expect(validator.validate('myapp123').errors.length).toEqual(0);
      expect(validator.validate('myapp123').warnings.length).toEqual(0);
    });
    it('Returns no warnings or errors for names that contain only alphanumeric characters and dashes', () => {
      expect(validator.validate('my-app-123').errors.length).toEqual(0);
      expect(validator.validate('my-app-123').warnings.length).toEqual(0);
    });
    it('Returns an error for names that contain other non-alphanumeric characters', () => {
      expect(validator.validate('my-app-123$').errors.length).toEqual(1);
      expect(validator.validate('my-app-123$').warnings.length).toEqual(0);
    });
  });
});

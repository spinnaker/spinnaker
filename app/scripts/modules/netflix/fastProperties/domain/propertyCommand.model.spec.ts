import {PropertyCommand} from './propertyCommand.model';
import {PropertyCommandType} from './propertyCommandType.enum';

describe('propertyCommand model', function () {

  describe('get button label', function () {
    it('for CREATE', function () {
      let propCommand: PropertyCommand = new PropertyCommand();
      propCommand.type = PropertyCommandType.CREATE;
      expect(propCommand.submitButtonLabel()).toBe('Create');
    });

    it('for UPDATE', function () {
      let propCommand: PropertyCommand = new PropertyCommand();
      propCommand.type = PropertyCommandType.UPDATE;
      expect(propCommand.submitButtonLabel()).toBe('Update');
    });

    it('for DELETE', function () {
      let propCommand: PropertyCommand = new PropertyCommand();
      propCommand.type = PropertyCommandType.DELETE;
      expect(propCommand.submitButtonLabel()).toBe('Delete');
    });

    it('for undefined propertyCommandType', function() {
      let propCommand: PropertyCommand = new PropertyCommand();
      propCommand.type = undefined;
      expect(propCommand.submitButtonLabel()).toBe('Submit');
    });

    it('for null propertyCommandType', function() {
      let propCommand: PropertyCommand = new PropertyCommand();
      propCommand.type = null;
      expect(propCommand.submitButtonLabel()).toBe('Submit');
    });
  });
});

import {PropertyCommand} from './propertyCommand.model';
import {PropertyCommandType} from './propertyCommandType.enum';
import {Scope} from './scope.domain';

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

  describe('check to move to new scope', function () {
    it('is move to new scope if originalScope and selectedScope are different', function () {
      let command: PropertyCommand = new PropertyCommand();
      let origScope = new Scope();
      origScope.env = 'prod';
      origScope.appId = 'mahe';
      let selectedScope = new Scope();
      selectedScope.env = 'prod';
      selectedScope.appId = 'newApp';

      command.originalScope = origScope;
      command.scopes = [selectedScope];

      expect(command.isMoveToNewScope()).toBe(true);
    });

    it('is not a move to new scope if originalScope and selectedScope are same but have different instance counts', function () {
      let command: PropertyCommand = new PropertyCommand();
      let origScope = new Scope();
      origScope.env = 'prod';
      origScope.appId = 'mahe';
      origScope.instanceCounts = {up: 1};
      let selectedScope = new Scope();
      selectedScope.env = 'prod';
      selectedScope.appId = 'mahe';
      selectedScope.instanceCounts = {up: 99};

      command.originalScope = origScope;
      command.scopes = [selectedScope];

      expect(command.isMoveToNewScope()).toBe(false);
    });


    it('is not a move to a new scope if the originalScope and the selected scope are the same ', function () {
      let command: PropertyCommand = new PropertyCommand();
      let scope = new Scope();
      scope.env = 'prod';
      scope.appId = 'mahe';

      command.originalScope = scope;
      command.scopes = [scope];

      expect(command.isMoveToNewScope()).toBe(false);
    });

    it('is not a move to a new scope if there is no new selected scope', function () {
      let command: PropertyCommand = new PropertyCommand();
      let scope = new Scope();
      scope.env = 'prod';
      scope.appId = 'mahe';

      command.originalScope = scope;
      command.scopes = [];

      expect(command.isMoveToNewScope()).toBe(false);
    });

    it('is not a move to a new scope if there is no new scope', function () {
      let command: PropertyCommand = new PropertyCommand();
      let scope = new Scope();
      scope.env = 'prod';
      scope.appId = 'mahe';

      command.originalScope = null;
      command.scopes = [scope];

      expect(command.isMoveToNewScope()).toBe(false);
    });
  });

});

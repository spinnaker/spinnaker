import {mock} from 'angular';

import {FastPropertyDetailsComponentController, FAST_PROPERTY_DETAILS_COMPONENT} from './propertyDetails.component';
import {PropertyCommand} from '../../domain/propertyCommand.model';
import {Property} from '../../domain/property.domain';

describe('propertyDetailsComponent test', function () {

  let $componentController: ng.IComponentControllerService,
    $ctrl: FastPropertyDetailsComponentController;

  let initializeController = (data: any) => {
    $ctrl = <FastPropertyDetailsComponentController> $componentController(
      'fastPropertyDetails',
      { $scope: null},
      data
    );
  };

  beforeEach(mock.module(FAST_PROPERTY_DETAILS_COMPONENT));

  beforeEach(
    mock
      .inject(( _$componentController_: ng.IComponentControllerService) => {
        $componentController = _$componentController_;
      })
  );

  describe('FastPropertyDetailsComponentController', function () {
    it('constructor should handle missing property command', function () {
      let data: {command: PropertyCommand, isEditing: boolean, isDeleting: boolean} = {
        command: null,
        isEditing: false,
        isDeleting: false,
      };

      expect(() => initializeController(data)).not.toThrow();

    });

    it('constructor should handle missing property on the PropertyCommand', function () {
      let data: {command: PropertyCommand, isEditing: boolean, isDeleting: boolean} = {
        command: new PropertyCommand(),
        isEditing: false,
        isDeleting: false,
      };

      expect(() => initializeController(data)).not.toThrow();

    });

    it('command with property that has a propertyId should copy it to originalProperty field on the command', function () {
      let data: {command: PropertyCommand, isEditing: boolean, isDeleting: boolean} = {
        command: new PropertyCommand(),
        isEditing: false,
        isDeleting: false,
      };

      data.command.property = <Property>{propertyId: '123'};

      initializeController(data);

      expect(data.command.originalProperty).not.toBeUndefined();
      expect(data.command.originalProperty.propertyId).toBe('123');

    });
  });

});

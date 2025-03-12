'use strict';

import * as angular from 'angular';
import { ModalWizard } from './ModalWizard';

describe('Service: wizardSubFormValidation', function () {
  let wizardSubFormValidation, $rootScope, $compile;

  beforeEach(window.module(require('./wizardSubFormValidation.service').name));

  beforeEach(
    window.inject(function (_wizardSubFormValidation_, _$rootScope_, _$compile_) {
      wizardSubFormValidation = _wizardSubFormValidation_;
      $rootScope = _$rootScope_;
      $compile = _$compile_;

      spyOn(ModalWizard, 'getPage').and.returnValue(true);
    }),
  );

  describe('wizardSubFormValidation.config', function () {
    let scope, formString;

    beforeEach(function () {
      scope = $rootScope.$new();
      formString = 'myTopLevelForm';
    });

    it('should assign scope and form name string', function () {
      wizardSubFormValidation.config({ scope: scope, form: formString });

      expect(wizardSubFormValidation.scope).toEqual(scope);
      expect(wizardSubFormValidation.form).toEqual(formString);
    });

    it('should hook into $scope destroy event and reset scope and form', function () {
      wizardSubFormValidation.config({ scope: scope, form: formString });

      expect(wizardSubFormValidation.scope).toEqual(scope);
      expect(wizardSubFormValidation.form).toEqual(formString);

      scope.$emit('destroy');

      expect(wizardSubFormValidation.scope).toBeUndefined();
      expect(wizardSubFormValidation.form).toBeUndefined();
    });
  });

  describe('wizardSubFormValidation.register', function () {
    let form, scope;

    beforeEach(function () {
      scope = $rootScope.$new();
      let formName = 'myTopLevelForm';
      let subFormName = 'mySubForm';

      wizardSubFormValidation.config({ scope: scope, form: formName });

      let element = angular.element(
        `<form name="${formName}">
             <ng-form name="${subFormName}">
               <input name="myInput" required />
             </ng-form>
           </form>`,
      );

      $compile(element)(scope);

      form = scope.myTopLevelForm;
    });

    afterEach(function () {
      scope.$emit('destroy');
    });

    it('registers page and sub-form; calls v2ModalWizard.markIncomplete on page if sub-form is invalid', function () {
      spyOn(ModalWizard, 'markIncomplete');

      wizardSubFormValidation.register({ page: 'myPage', subForm: 'mySubForm' });
      form.mySubForm.$setValidity('myInput.required', false);
      scope.$digest();

      expect(ModalWizard.markIncomplete).toHaveBeenCalledWith('myPage');
    });

    it('registers page and sub-form; calls v2ModalWizard.markComplete on page if sub-form is valid', function () {
      spyOn(ModalWizard, 'markComplete');

      wizardSubFormValidation.register({ page: 'myPage', subForm: 'mySubForm' });
      form.mySubForm.$setValidity('myInput.required', true);
      scope.$digest();

      expect(ModalWizard.markComplete).toHaveBeenCalledWith('myPage');
    });
  });
});

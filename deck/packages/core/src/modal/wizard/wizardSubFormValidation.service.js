'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { ModalWizard } from './ModalWizard';

/**
 * Propagates standard Angular form validation to ModalWizard.
 */

export const CORE_MODAL_WIZARD_WIZARDSUBFORMVALIDATION_SERVICE = 'spinnaker.core.modalWizard.subFormValidation.service';
export const name = CORE_MODAL_WIZARD_WIZARDSUBFORMVALIDATION_SERVICE; // for backwards compatibility
module(CORE_MODAL_WIZARD_WIZARDSUBFORMVALIDATION_SERVICE, []).factory('wizardSubFormValidation', function () {
  let validatorRegistry = {};

  function buildWatchString(form, subForm, formKey) {
    return `${form}.${subForm}.${formKey}`;
  }

  this.config = ({ form, scope }) => {
    this.form = form;
    this.scope = scope;
    this.scope.$on('destroy', this.reset);
    return this;
  };

  this.register = ({ subForm, page, validators = [] }) => {
    validators.push({
      watchString: buildWatchString(this.form, subForm, '$valid'),
      validator: (subFormIsValid) => subFormIsValid,
    });

    validatorRegistry[page] = validators.map((v) => new Validator(v, this.scope, page));

    return this;
  };

  this.subFormsAreValid = () => {
    return _.every(validatorRegistry, (validatorsForPage) => validatorsForPage.every((v) => v.state.valid));
  };

  this.reset = () => {
    validatorRegistry = {};
    this.scope = undefined;
    this.form = undefined;
  };

  class Validator {
    constructor({ watchString, validator, collection, watchDeep }, scope, page, state = { valid: false }) {
      this.state = state;
      this.page = page;

      const watchType = collection ? '$watchCollection' : '$watch';

      scope[watchType](
        watchString,
        (value) => {
          this.state.valid = validator(value);

          if (ModalWizard.getPage(this.page)) {
            if (this.state.valid) {
              this.emitValid();
            } else {
              this.emitInvalid();
            }
          }
        },
        watchDeep || false,
      );
    }

    emitValid() {
      const pageIsValid = validatorRegistry[this.page].every((v) => v.state.valid);

      if (pageIsValid) {
        ModalWizard.markComplete(this.page);
      }
    }

    emitInvalid() {
      ModalWizard.markIncomplete(this.page);
    }
  }

  return this;
});

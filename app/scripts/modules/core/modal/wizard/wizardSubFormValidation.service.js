'use strict';

import _ from 'lodash';
import {V2_MODAL_WIZARD_SERVICE} from './v2modalWizard.service';

let angular = require('angular');

/**
 * Propagates standard Angular form validation to v2modalWizardService.
 */

module.exports = angular.module('spinnaker.core.modalWizard.subFormValidation.service', [
    V2_MODAL_WIZARD_SERVICE,
  ])
  .factory('wizardSubFormValidation', function(v2modalWizardService) {
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
        validator: subFormIsValid => subFormIsValid
      });

      validatorRegistry[page] = validators.map(v => new Validator(v, this.scope, page));

      return this;
    };

    this.subFormsAreValid = () => {
      return _.every(validatorRegistry, validatorsForPage => validatorsForPage.every(v => v.state.valid));
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

        let watchType = collection ? '$watchCollection' : '$watch';

        scope[watchType](watchString, (value) => {
          this.state.valid = validator(value);

          if (v2modalWizardService.getPage(this.page)) {
            if (this.state.valid) {
              this.emitValid();
            } else {
              this.emitInvalid();
            }
          }
        }, watchDeep || false);
      }

      emitValid() {
        let pageIsValid = validatorRegistry[this.page]
          .every(v => v.state.valid);

        if (pageIsValid) {
          v2modalWizardService.markComplete(this.page);
        }
      }

      emitInvalid() {
        v2modalWizardService.markIncomplete(this.page);
      }
    }

    return this;
  });

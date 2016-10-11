'use strict';

import _ from 'lodash';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperty.wizard.managent.service', [
    require('core/modal/wizard/modalWizard.service'),

  ])
  .factory('fastPropertyWizardManagementService', function(modalWizardService) {

    let defaultPages = ['Details', 'Scope', 'Strategy'];


    let excludeDefaultPages = () => {
      return modalWizardService.getWizard().renderedPages.filter((page) => {
        return !_.includes(defaultPages, page.key);
      });
    };

    let getPagesKeysToShow = (selectedKeys = []) => {
      let keys = excludeDefaultPages().map( page => page.key );
      return selectedKeys.reduce((acc, key) => {
        if(!_.includes(acc, key)) {
          acc.push(key);
        }
        return acc;
      }, keys);

    };

    let getPagesToHide = (seletedKeys = []) => {
      let keys = excludeDefaultPages().map(page => page.key);
      return _.difference(keys, seletedKeys);
    };

    let hidePages = (selectedKeys) => {
      let pages = getPagesToHide(selectedKeys);
      pages.forEach( modalWizardService.getWizard().excludePage );
    };

    let showPages = (selectedKeys) => {
      let pages = getPagesKeysToShow(selectedKeys);
      pages.forEach( modalWizardService.getWizard().includePage );
    };

    return {
      getPagesKeysToShow: getPagesKeysToShow,
      getPagesToHide: getPagesToHide,
      hidePages: hidePages,
      showPages: showPages,
    };

  });


'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProperty.wizard.managent.service', [
    require('../../../core/utils/lodash.js'),
    require('../../../core/modal/wizard/modalWizard.service'),

  ])
  .factory('fastPropertyWizardManagementService', function(_, modalWizardService) {

    let defaultPages = ['Details', 'Scope', 'Strategy'];


    let excludeDefaultPages = () => {
      return modalWizardService.getWizard().renderedPages.filter((page) => {
        return !_.contains(defaultPages, page.key);
      });
    };

    let getPagesKeysToShow = (selectedKeys = []) => {
      let keys = excludeDefaultPages().map( page => page.key );
      return selectedKeys.reduce((acc, key) => {
        if(!_.contains(acc, key)) {
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


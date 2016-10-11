'use strict';


describe('fastPropertyWizardMangagement.service', function () {

  let service;
  let wizardService;
  let defaultPages = ['Details', 'Scope', 'Strategy'];

  beforeEach(window.module(
    require('./fastPropertyWizardManagement.service'),
    require('core/modal/wizard/modalWizard.service')
  ));

  beforeEach(
    window.inject(function(fastPropertyWizardManagementService, modalWizardService) {
      service = fastPropertyWizardManagementService;
      wizardService = modalWizardService;
    })
  );

  it('should have an instantiated service', function () {
    expect(service).toBeDefined();
    expect(wizardService).toBeDefined();
  });


  let registerPage = (pageKey) => {
    wizardService.getWizard().registerPage(pageKey, pageKey, {rendered: true});
  };

  describe('get pages to show in wizard', function () {
    beforeEach(() => {
      defaultPages.forEach((page) => {
        registerPage(page);
      });
    });

    it('should return an empty list of the strategies pages to show if only the defaults are selected', function () {
      let result = service.getPagesKeysToShow();
      expect(result).toEqual([]);
    });

    it('should return a list of the strategies pages selected minus the defaults', function () {
      registerPage('Target');

      let result = service.getPagesKeysToShow(['Target']);
      expect(result).toEqual(['Target']);
    });

    it('should return a list of the strategies pages selected minus the defaults', function () {
      registerPage('Target');

      let result = service.getPagesKeysToShow(['Target', 'Review']);
      expect(result).toEqual(['Target', 'Review']);
    });
  });

  describe('get pages to hide in wizard', function () {
    beforeEach(() => {
      defaultPages.forEach((page) => {
        registerPage(page);
      });
    });

    it('should return an empty set if the strategy pages match the default list', function () {
      let result = service.getPagesToHide();
      expect(result).toEqual([]);
    });

    it('should return an empty set if the strategy pages match the rendered pages', function () {
      registerPage('Target');

      let result = service.getPagesToHide(['Target']);

      expect(result).toEqual([]);
    });

    it('should return an array if the strategy pages exclude the rendered pages', function () {
      registerPage('Target');

      let result = service.getPagesToHide(['Review']);

      expect(result).toEqual(['Target']);
    });

    it('should return an array if the strategy pages exclude the rendered pages', function () {
      registerPage('Target');
      registerPage('Something');

      let result = service.getPagesToHide(['Review']);

      expect(result).toEqual(['Target', 'Something']);
    });
  });

});

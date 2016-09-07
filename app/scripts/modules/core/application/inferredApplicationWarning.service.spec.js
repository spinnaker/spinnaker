'use strict';

describe('Service: inferredApplicationWarning', function () {
  let inferredApplicationWarning, notifierService;

  beforeEach(
    window.module(
      require('./inferredApplicationWarning.service.js'),
      require('../utils/rx.js')
    )
  );

  beforeEach(
    window.inject(function (_inferredApplicationWarning_, _notifierService_) {
      inferredApplicationWarning = _inferredApplicationWarning_;
      notifierService = _notifierService_;
    })
  );

  describe('checkIfInferredAndWarn', function () {
    let configuredApp, inferredApp;

    beforeEach(function () {
      configuredApp = {
        name: 'myConfiguredApp',
        attributes: {
          accounts: 'my-google-account',
          email: 'email@email.email',
        }
      };

      inferredApp = {
        name: 'myInferredApp',
        attributes: {},
      };

      inferredApplicationWarning.resetViewedApplications();
      spyOn(notifierService, 'publish');
    });

    it('should warn a user when an application is inferred (i.e., missing attributes)', function () {
      inferredApplicationWarning.checkIfInferredAndWarn(inferredApp);

      expect(notifierService.publish).toHaveBeenCalled();
    });

    it('should not warn a user when an application is properly configured (i.e., not missing attributes)', function () {
      inferredApplicationWarning.checkIfInferredAndWarn(configuredApp);

      expect(notifierService.publish).not.toHaveBeenCalled();
    });

    it('should not warn a user more than once about an inferred application', function () {
      inferredApplicationWarning.checkIfInferredAndWarn(inferredApp);
      inferredApplicationWarning.checkIfInferredAndWarn(inferredApp);
      inferredApplicationWarning.checkIfInferredAndWarn(inferredApp);

      expect(notifierService.publish.calls.count()).toEqual(1);
    });
  });
});

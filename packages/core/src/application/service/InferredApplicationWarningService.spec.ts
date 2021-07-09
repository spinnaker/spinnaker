import Spy = jasmine.Spy;

import { NotifierService } from '../../widgets/notifier/notifier.service';

import { InferredApplicationWarningService } from './InferredApplicationWarningService';
import { Application } from '../application.model';
import { ApplicationModelBuilder } from '../applicationModel.builder';

describe('Service: inferredApplicationWarning', () => {
  describe('checkIfInferredAndWarn', () => {
    let configuredApp: Application, inferredApp: Application;

    beforeEach(function () {
      configuredApp = ApplicationModelBuilder.createApplicationForTests('myConfiguredApp');
      configuredApp.attributes.email = 'email@email.email';

      inferredApp = ApplicationModelBuilder.createNotFoundApplication('myInferredApp');

      InferredApplicationWarningService.resetViewedApplications();
      spyOn(NotifierService, 'publish');
    });

    it('should warn a user when an application is inferred (i.e., missing attributes)', () => {
      InferredApplicationWarningService.checkIfInferredAndWarn(inferredApp);

      expect(NotifierService.publish).toHaveBeenCalled();
    });

    it('should not warn a user when an application is properly configured (i.e., not missing attributes)', () => {
      InferredApplicationWarningService.checkIfInferredAndWarn(configuredApp);

      expect(NotifierService.publish).not.toHaveBeenCalled();
    });

    it('should not warn a user more than once about an inferred application', () => {
      InferredApplicationWarningService.checkIfInferredAndWarn(inferredApp);
      InferredApplicationWarningService.checkIfInferredAndWarn(inferredApp);
      InferredApplicationWarningService.checkIfInferredAndWarn(inferredApp);

      expect((NotifierService.publish as Spy).calls.count()).toEqual(1);
    });
  });
});

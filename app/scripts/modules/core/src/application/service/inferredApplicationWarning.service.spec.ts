import { mock } from 'angular';

import Spy = jasmine.Spy;

import {
  INFERRED_APPLICATION_WARNING_SERVICE,
  InferredApplicationWarningService,
} from './inferredApplicationWarning.service';
import { NotifierService } from 'core/widgets/notifier/notifier.service';
import { Application } from '../application.model';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from '../applicationModel.builder';

describe('Service: inferredApplicationWarning', () => {
  let inferredApplicationWarningService: InferredApplicationWarningService,
    applicationModelBuilder: ApplicationModelBuilder;

  beforeEach(mock.module(APPLICATION_MODEL_BUILDER, INFERRED_APPLICATION_WARNING_SERVICE));

  beforeEach(
    mock.inject(
      (
        _inferredApplicationWarningService_: InferredApplicationWarningService,
        _applicationModelBuilder_: ApplicationModelBuilder,
      ) => {
        inferredApplicationWarningService = _inferredApplicationWarningService_;
        applicationModelBuilder = _applicationModelBuilder_;
      },
    ),
  );

  describe('checkIfInferredAndWarn', () => {
    let configuredApp: Application, inferredApp: Application;

    beforeEach(function() {
      configuredApp = applicationModelBuilder.createApplication('myConfiguredApp', []);
      configuredApp.attributes.email = 'email@email.email';

      inferredApp = applicationModelBuilder.createNotFoundApplication('myInferredApp');

      inferredApplicationWarningService.resetViewedApplications();
      spyOn(NotifierService, 'publish');
    });

    it('should warn a user when an application is inferred (i.e., missing attributes)', () => {
      inferredApplicationWarningService.checkIfInferredAndWarn(inferredApp);

      expect(NotifierService.publish).toHaveBeenCalled();
    });

    it('should not warn a user when an application is properly configured (i.e., not missing attributes)', () => {
      inferredApplicationWarningService.checkIfInferredAndWarn(configuredApp);

      expect(NotifierService.publish).not.toHaveBeenCalled();
    });

    it('should not warn a user more than once about an inferred application', () => {
      inferredApplicationWarningService.checkIfInferredAndWarn(inferredApp);
      inferredApplicationWarningService.checkIfInferredAndWarn(inferredApp);
      inferredApplicationWarningService.checkIfInferredAndWarn(inferredApp);

      expect((NotifierService.publish as Spy).calls.count()).toEqual(1);
    });
  });
});

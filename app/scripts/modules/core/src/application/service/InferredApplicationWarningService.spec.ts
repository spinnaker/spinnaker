import { mock } from 'angular';

import Spy = jasmine.Spy;

import { NotifierService } from 'core/widgets/notifier/notifier.service';

import { InferredApplicationWarningService } from './InferredApplicationWarningService';
import { Application } from '../application.model';
import { APPLICATION_MODEL_BUILDER, ApplicationModelBuilder } from '../applicationModel.builder';

describe('Service: inferredApplicationWarning', () => {
  let applicationModelBuilder: ApplicationModelBuilder;

  beforeEach(mock.module(APPLICATION_MODEL_BUILDER));

  beforeEach(
    mock.inject((_applicationModelBuilder_: ApplicationModelBuilder) => {
      applicationModelBuilder = _applicationModelBuilder_;
    }),
  );

  describe('checkIfInferredAndWarn', () => {
    let configuredApp: Application, inferredApp: Application;

    beforeEach(function() {
      configuredApp = applicationModelBuilder.createApplicationForTests('myConfiguredApp');
      configuredApp.attributes.email = 'email@email.email';

      inferredApp = applicationModelBuilder.createNotFoundApplication('myInferredApp');

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

import type { Mock } from 'vitest';
import Spy = Mock;

import { InferredApplicationWarningService } from './InferredApplicationWarningService';
import type { Application } from '../application.model';
import { ApplicationModelBuilder } from '../applicationModel.builder';
import { NotifierService } from '../../widgets/notifier/notifier.service';

describe('Service: inferredApplicationWarning', () => {
  describe('checkIfInferredAndWarn', () => {
    let configuredApp: Application, inferredApp: Application;

    beforeEach(function () {
      configuredApp = ApplicationModelBuilder.createApplicationForTests('myConfiguredApp');
      configuredApp.attributes.email = 'email@email.email';

      inferredApp = ApplicationModelBuilder.createNotFoundApplication('myInferredApp');

      InferredApplicationWarningService.resetViewedApplications();
      vi.spyOn(NotifierService, 'publish').mockReturnValue(undefined);
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

      expect((NotifierService.publish as Spy).mock.calls.length).toEqual(1);
    });
  });
});

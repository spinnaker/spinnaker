import { IArtifact, IExpectedArtifact } from '@spinnaker/core';

import { validateInputArtifacts, validateProducedArtifacts } from './BakeCloudFoundryManifestConfig';

describe('Bake Cloud Foundry Form Validators', () => {
  describe('Validate Input Artifacts', () => {
    it('should fail when given empty input artifacts', () => {
      const inputArtifacts: IArtifact[] = [];
      expect(validateInputArtifacts(inputArtifacts)).toBe(false);
    });

    it('should fail when given less than 2 input artifacts', () => {
      const inputArtifacts: IArtifact[] = [
        {
          id: 'abc123',
        },
      ];
      expect(validateInputArtifacts(inputArtifacts)).toBe(false);
    });

    it('should pass when given 2 or more input artifacts', () => {
      const inputArtifacts: IArtifact[] = [
        {
          id: 'abc123',
        },
        {
          id: 'abc12345',
        },
        {
          id: 'abc12345678',
        },
      ];
      expect(validateInputArtifacts(inputArtifacts)).toBe(true);
    });
  });
  describe('Validate Expected Artifacts', () => {
    it('should fail when expected artifacts are empty', () => {
      const expectedArtifacts: IExpectedArtifact[] = [];
      expect(validateProducedArtifacts(expectedArtifacts)).toBe(false);
    });

    it('should pass when there is 1 expected artifact of type base64', () => {
      const expectedArtifacts: IExpectedArtifact[] = [
        {
          id: 'abc123',
          matchArtifact: {
            type: 'embedded/base64',
            id: 'abc123',
          },
          usePriorArtifact: false,
          useDefaultArtifact: true,
          defaultArtifact: {
            id: 'abc123',
          },
          displayName: 'temp',
        },
      ];
      expect(validateProducedArtifacts(expectedArtifacts)).toBe(true);
    });

    it('should fail when there is expected artifact of wrong type', () => {
      const expectedArtifacts: IExpectedArtifact[] = [
        {
          id: 'abc123',
          matchArtifact: {
            type: 'NOTembedded/base64',
            id: 'abc123',
          },
          usePriorArtifact: false,
          useDefaultArtifact: true,
          defaultArtifact: {
            id: 'abc123',
          },
          displayName: 'temp',
        },
      ];
      expect(validateProducedArtifacts(expectedArtifacts)).toBe(false);
    });
  });
});

import { KubernetesV2DeployManifestConfigCtrl as Controller } from './deployManifestConfig.controller';

const basicManifest = `
kind: Deployment
apiVersion: foo/bar
`;

const singleManifestArray = `
- kind: Deployment
  apiVersion: foo/bar
`;

const multipleManifestArray = `
- kind: Deployment
  apiVersion: foo/bar
- kind: Service
  apiVersion: foo/bar
`;

const multipleManifestDocuments = `
kind: Deployment
apiVersion: foo/bar
---
kind: Service
apiVersion: foo/bar
`;

describe('KubernetesV2DeployManifestConfigCtrl', function() {
  let metadata: any;
  let builtCmd: any;
  let builtCmdPromise: any;
  let stage: any;
  let scope: any;
  let cmdBuilder: any;
  let artSvc: any;

  beforeEach(function() {
    metadata = {};
    builtCmd = { metadata, command: {} };
    builtCmdPromise = Promise.resolve(builtCmd);
    stage = { manifests: [] };
    scope = {
      ctrl: { metadata },
      stage,
      $parent: {
        pipeline: {},
      },
    };
    cmdBuilder = {
      buildNewManifestCommand: () => builtCmdPromise,
    };
    artSvc = {
      getExpectedArtifactsAvailableToStage: (): any[] => [],
    };
  });

  describe('change', function() {
    it('normalizes yaml doc with a single manifest into an array', function(done) {
      const ctrl = new Controller(scope, cmdBuilder, artSvc);
      builtCmdPromise.then(() => {
        ctrl.metadata.manifestText = basicManifest;
        ctrl.change();
        expect(ctrl.metadata.yamlError).toBe(false);
        expect(scope.stage.manifests.length).toBe(1);
        expect(scope.stage.manifests[0].kind).toBe('Deployment');
        done();
      });
    });

    it('normalizes a yaml doc with a single manifest in an array into a flat array', function(done) {
      const ctrl = new Controller(scope, cmdBuilder, artSvc);
      builtCmdPromise.then(() => {
        ctrl.metadata.manifestText = singleManifestArray;
        ctrl.change();
        expect(ctrl.metadata.yamlError).toBe(false);
        expect(scope.stage.manifests.length).toBe(1);
        expect(scope.stage.manifests[0].kind).toBe('Deployment');
        done();
      });
    });

    it('normalizes a yaml doc with multiple manifest entries in an array into a flat array', function(done) {
      const ctrl = new Controller(scope, cmdBuilder, artSvc);
      builtCmdPromise.then(() => {
        ctrl.metadata.manifestText = multipleManifestArray;
        ctrl.change();
        expect(ctrl.metadata.yamlError).toBe(false);
        expect(scope.stage.manifests.length).toBe(2);
        expect(scope.stage.manifests[0].kind).toBe('Deployment');
        expect(scope.stage.manifests[1].kind).toBe('Service');
        done();
      });
    });

    it('normalizes a yaml doc with multiple manifest documents into a flat array', function(done) {
      const ctrl = new Controller(scope, cmdBuilder, artSvc);
      builtCmdPromise.then(() => {
        ctrl.metadata.manifestText = multipleManifestDocuments;
        ctrl.change();
        expect(ctrl.metadata.yamlError).toBe(false);
        expect(scope.stage.manifests.length).toBe(2);
        expect(scope.stage.manifests[0].kind).toBe('Deployment');
        expect(scope.stage.manifests[1].kind).toBe('Service');
        done();
      });
    });
  });
});

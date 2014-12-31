'use strict';

describe('serverGroupWriter', function () {

  var serverGroupWriter,
    $httpBackend,
    settings;

  beforeEach(function () {
    loadDeckWithoutCacheInitializer();
    inject(function (_serverGroupWriter_, _$httpBackend_, _settings_) {
      serverGroupWriter = _serverGroupWriter_;
      $httpBackend = _$httpBackend_;
      settings = _settings_;
    });
  });

  describe('clone server group submit', function () {

    function postTask(command) {
      var submitted = null;
      $httpBackend.expectPOST(settings.gateUrl + '/applications/appName/tasks', function (bodyString) {
        submitted = angular.fromJson(bodyString);
        return true;
      }).respond(200, {ref: '/1'});

      $httpBackend.expectGET(settings.gateUrl + '/applications/appName/tasks/1').respond({});

      serverGroupWriter.cloneServerGroup(command, 'appName');
      $httpBackend.flush();

      return submitted;
    }

    it('sets amiName from allImageSelection', function () {
      var command = {
          viewState: {
            mode: 'create',
            useAllImageSelection: true,
            allImageSelection: 'something-packagebase',
          }
        };

      var submitted = postTask(command);

      expect(submitted.job[0].amiName).toBe('something-packagebase');

    });

    it('removes subnetType property when null', function () {
      var command = {
          viewState: {
            mode: 'create',
            useAllImageSelection: true,
            allImageSelection: 'something-packagebase',
          },
          subnetType: null,
        };

      var submitted = postTask(command);
      expect(submitted.job[0].subnetType).toBe(undefined);

      command.subnetType = 'internal';
      submitted = postTask(command);
      expect(submitted.job[0].subnetType).toBe('internal');
    });

    it('sets action type and description appropriately when creating new', function () {
      var command = {
          viewState: {
            mode: 'create',
          },
        };

      var submitted = postTask(command);
      expect(submitted.job[0].type).toBe('deploy');
      expect(submitted.description).toBe('Create New Server Group in cluster appName');

      command.stack = 'main';
      submitted = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster appName-main');

      command.freeFormDetails = 'details';
      submitted = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster appName-main-details');

      delete command.stack;
      submitted = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster appName--details');
    });

    it('sets action type and description appropriately when cloning, preserving source', function () {
      var command = {
          viewState: {
            mode: 'clone',
          },
          source: {
            asgName: 'appName-v002',
          }
        };

      var submitted = postTask(command);
      expect(submitted.job[0].type).toBe('copyLastAsg');
      expect(submitted.description).toBe('Create Cloned Server Group from appName-v002');
      expect(submitted.job[0].source).toEqual(command.source);
    });
  });
});

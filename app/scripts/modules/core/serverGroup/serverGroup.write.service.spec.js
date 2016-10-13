'use strict';

import {API_SERVICE} from 'core/api/api.service';

describe('serverGroupWriter', function () {
  const angular = require('angular');

  var serverGroupWriter,
    $httpBackend,
    serverGroupTransformer,
    API;

  beforeEach(
    window.module(
      require('./serverGroup.write.service.js'),
      API_SERVICE
    )
  );

  beforeEach(function () {
    window.inject(function (_serverGroupWriter_, _$httpBackend_, _serverGroupTransformer_, _API_) {
      API = _API_;
      serverGroupWriter = _serverGroupWriter_;
      $httpBackend = _$httpBackend_;
      serverGroupTransformer = _serverGroupTransformer_;
    });
  });

  beforeEach(function() {
    spyOn(serverGroupTransformer, 'convertServerGroupCommandToDeployConfiguration').and.callFake((command) => { return command; });
  });


  describe('clone server group submit', function () {

    function postTask(command) {
      var submitted = null;
      $httpBackend.expectPOST(API.baseUrl + '/applications/appName/tasks', function (bodyString) {
        submitted = angular.fromJson(bodyString);
        return true;
      }).respond(200, {ref: '/1'});

      $httpBackend.expectGET(API.baseUrl + '/tasks/1').respond({});

      serverGroupWriter.cloneServerGroup(command, { name: 'appName', tasks: { refresh: angular.noop } });

      $httpBackend.flush();

      return submitted;
    }

    var command = {
      viewState: {
        mode: 'create',
      },
      application: { name: 'theApp'}
    };

    it('sets action type and description appropriately when creating new', function () {
      var submitted = postTask(command);
      expect(submitted.job[0].type).toBe('createServerGroup');
      expect(submitted.description).toBe('Create New Server Group in cluster appName');
    });

    it('sets action type and description appropriately when creating new', function () {
      command.stack = 'main';
      let submitted = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster appName-main');
    });

    it('sets action type and description appropriately when creating new', function () {
      command.freeFormDetails = 'details';
      let submitted = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster appName-main-details');
    });

    it('sets action type and description appropriately when creating new', function () {
      delete command.stack;
      let submitted = postTask(command);
      expect(submitted.description).toBe('Create New Server Group in cluster appName--details');
    });

    it('sets action type and description appropriately when cloning, preserving source', function () {
      var command = {
          viewState: {
            mode: 'clone',
          },
          source: {
            asgName: 'appName-v002',
          },
          application: { name: 'theApp'}
        };

      var submitted = postTask(command);
      expect(submitted.job[0].type).toBe('cloneServerGroup');
      expect(submitted.description).toBe('Create Cloned Server Group from appName-v002');
      expect(submitted.job[0].source).toEqual(command.source);
    });
  });
});

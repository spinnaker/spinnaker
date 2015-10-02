'use strict';

describe('Service: applicationReader', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var applicationReader;
  var application;

  beforeEach(
    window.module(
      require('./applications.read.service')
    )
  );

  beforeEach(
    window.inject(function (_applicationReader_) {
      applicationReader = _applicationReader_;
      application = {};
    })
  );

  it('should instantiate the controller', function () {
    expect(applicationReader).toBeDefined();
  });

  describe('adding exceptions to applications', function () {
    it('should add executions if they are an array', function () {
      let execs = [{a:1}];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual(execs);
    });

    it('should add and empty array if the execution is not an array', function () {
      let execs = {a:1};

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual([]);
    });

    it('should not replace the existing executions if application has executions and the reloaded one is bad', function () {
      let execs = {a:2};
      application.executions = [{a:1}];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).not.toEqual([]);
      expect(application.executions).toEqual([{a:1}]);
    });

    it('should not replace the existing executions if application has executions comes back empty', function () {
      let execs = [];
      application.executions = [{a:1}];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).not.toEqual([]);
      expect(application.executions).toEqual([{a:1}]);
    });

    it('should set the executions after a bad set', function () {
      let execs = [];
      application.executions = [{a:1}];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).not.toEqual([]);
      expect(application.executions).toEqual([{a:1}]);

      let newExecs = [{a:2}];
      applicationReader.addExecutionsToApplication(application, newExecs);

      expect(application.executions).toEqual([{a:2}]);

    });
  });
});



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

  describe('adding executions to applications', function () {
    it('should add all executions if there are none on application', function () {
      let execs = [{a:1}];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toBe(execs);
    });

    it('should add new executions', function () {
      let original = {id:1, stringVal: 'ac'};
      let newOne = {id:2, stringVal: 'ab'};
      let execs = [original, newOne];
      application.executions = [original];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual([original, newOne]);
    });

    it('should replace an existing execution if stringVal has changed', function () {
      let original = {id:1, stringVal: 'ac'};
      let updated = {id:1, stringVal: 'ab'};
      let execs = [updated];
      application.executions = [original];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual([updated]);
    });

    it('should remove an execution if it is not in the new set', function () {
      let transient = {id:1, stringVal: 'ac'};
      let persistent = {id:2, stringVal: 'ab'};
      let execs = [persistent];
      application.executions = [transient];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual([persistent]);
    });

    it('should remove multiple executions if not in the new set', function () {
      let transient1 = {id:1, stringVal: 'ac'};
      let persistent = {id:2, stringVal: 'ab'};
      let transient3 = {id:3, stringVal: 'ac'};
      let execs = [persistent];
      application.executions = [transient1, persistent, transient3];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual([persistent]);
    });

    it('should replace the existing executions if application has executions comes back empty', function () {
      let execs = [];
      application.executions = [{a:1}];

      applicationReader.addExecutionsToApplication(application, execs);

      expect(application.executions).toEqual([]);
    });

  });
});



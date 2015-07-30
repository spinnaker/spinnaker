'use strict';


describe('Cluster Filter Model', function () {

  var ClusterFilterModel;

  beforeEach(window.module(
    require('./clusterFilterModel')
  ));

  beforeEach(
    window.inject(function(_ClusterFilterModel_){
      ClusterFilterModel = _ClusterFilterModel_;
    })
  );

  describe('convert sortFilter object to a string for the query param', function () {

    it('should convert an object with all true values to string', function () {
      var obj = {
        foo: true,
        bar: true
      };

      var result = ClusterFilterModel.convertObjectToParam(obj);

      expect(result).toEqual('foo,bar');
    });


    it('should convert an object with mixed true and false values to string', function () {

      var obj = {
        foo: true,
        bar: false
      };

      var result = ClusterFilterModel.convertObjectToParam(obj);
      expect(result).toEqual('foo');
    });


    it('should convert an object with mixed all false values to empty string', function () {
      var obj = {
        foo: false,
        bar: false
      };

      var result = ClusterFilterModel.convertObjectToParam(obj);
      expect(result).toEqual('');
    });


    it('should convert an empty object with mixed all false values to empty string', function () {
      var obj = {};

      var result = ClusterFilterModel.convertObjectToParam(obj);
      expect(result).toEqual('');
    });
  });
});

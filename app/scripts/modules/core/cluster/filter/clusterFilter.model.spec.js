'use strict';


describe('Cluster Filter Model', function () {

  var ClusterFilterModel;

  beforeEach(window.module(
    require('./clusterFilter.model')
  ));

  beforeEach(
    window.inject(function(_ClusterFilterModel_){
      ClusterFilterModel = _ClusterFilterModel_;
    })
  );
});

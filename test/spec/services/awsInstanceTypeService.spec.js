/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

describe('Service: InstanceType', function () {

  beforeEach(loadDeckWithoutCacheInitializer);

  beforeEach(inject(function (_instanceTypeService_, _$httpBackend_, _settings_) {

    this.instanceTypeService = _instanceTypeService_;
    this.$httpBackend = _$httpBackend_;
    this.settings = _settings_;

    this.allTypes = [
      {account: "test", region: "us-west-2", name: "m1.small", availabilityZone: "us-west-2a"},
      {account: "test", region: "us-west-2", name: "m2.xlarge", availabilityZone: "us-west-2b"},
      {account: "test", region: "eu-west-1", name: "hs1.8xlarge", availabilityZone: "eu-west-1c"},
      {account: "test", region: "eu-west-1", name: "m2.xlarge", availabilityZone: "eu-west-1c"},
    ];
  }));

  afterEach(function () {
    this.$httpBackend.verifyNoOutstandingRequest();
  });

  describe('getAllTypesByRegion', function () {

    it('returns types, indexed by region', function () {

      this.$httpBackend.expectGET(this.settings.gateUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null;
      this.instanceTypeService.getAllTypesByRegion().then(function(result) {
        results = result;
      });

      this.$httpBackend.flush();
      expect(results['us-west-2'].length).toBe(2);
      expect(_.pluck(results['us-west-2'], 'name').sort()).toEqual(['m1.small', 'm2.xlarge']);
    });

  });

  describe('getAvailableTypesForRegions', function() {

    it('returns results for a single region', function() {
      this.$httpBackend.expectGET(this.settings.gateUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null,
          service = this.instanceTypeService;

      this.instanceTypeService.getAllTypesByRegion().then(function(result) {
        results = service.getAvailableTypesForRegions('aws', result, ['us-west-2']);
      });

      this.$httpBackend.flush();
      expect(results).toEqual(['m1.small', 'm2.xlarge']);
    });

    it('returns empty list for region with no instance types', function() {
      this.$httpBackend.expectGET(this.settings.gateUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null,
          service = this.instanceTypeService;

      this.instanceTypeService.getAllTypesByRegion().then(function(result) {
        results = service.getAvailableTypesForRegions('aws', result, ['us-west-3']);
      });

      this.$httpBackend.flush();
      expect(results).toEqual([]);
    });

    it('returns an intersection when multiple regions are provided', function() {
      this.$httpBackend.expectGET(this.settings.gateUrl + '/instanceTypes').respond(200, this.allTypes);

      var results = null,
          service = this.instanceTypeService;

      this.instanceTypeService.getAllTypesByRegion().then(function(result) {
        results = service.getAvailableTypesForRegions('aws', result, ['us-west-2', 'eu-west-1']);
      });

      this.$httpBackend.flush();
      expect(results).toEqual(['m2.xlarge']);
    });

  });

});

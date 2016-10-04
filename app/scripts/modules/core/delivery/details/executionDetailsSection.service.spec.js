'use strict';

describe('executionDetailsSectionService', function() {
  beforeEach(window.module('spinnaker.executionDetails.section.service'));

  beforeEach(window.inject(function(executionDetailsSectionService, $state, $stateParams) {
    this.service = executionDetailsSectionService;
    this.$state = $state;
    this.$stateParams = $stateParams;
  }));

  describe('synchronizeSection', function() {
    it('does nothing when state is not in execution details', function() {
      spyOn(this.$state, 'includes').and.returnValue(false);
      spyOn(this.$state, 'go');

      this.service.synchronizeSection(['a', 'b']);

      expect(this.$state.includes).toHaveBeenCalledWith('**.execution');
      expect(this.$state.go).not.toHaveBeenCalled();
    });

    it('reuses current section if still valid', function() {
      spyOn(this.$state, 'includes').and.returnValue(true);
      spyOn(this.$state, 'go');

      this.$stateParams.details = 'b';

      this.service.synchronizeSection(['a', 'b']);

      expect(this.$state.includes).toHaveBeenCalledWith('**.execution');
      expect(this.$state.go).not.toHaveBeenCalled();
    });

    it('replaces current section if not valid', function() {
      spyOn(this.$state, 'includes').and.returnValue(true);
      spyOn(this.$state, 'go');

      this.$stateParams.details = 'c';

      this.service.synchronizeSection(['a', 'b']);

      expect(this.$state.includes).toHaveBeenCalledWith('**.execution');
      expect(this.$state.go).toHaveBeenCalledWith('.', { details: 'a'}, {location: 'replace'});
    });


    it('uses first section if none present in state params', function() {
      spyOn(this.$state, 'includes').and.returnValue(true);
      spyOn(this.$state, 'go');

      this.$stateParams.details = undefined;

      this.service.synchronizeSection(['a', 'b']);

      expect(this.$state.includes).toHaveBeenCalledWith('**.execution');
      expect(this.$state.go).toHaveBeenCalledWith('.', { details: 'a'}, {location: 'replace'});
    });

  });

});

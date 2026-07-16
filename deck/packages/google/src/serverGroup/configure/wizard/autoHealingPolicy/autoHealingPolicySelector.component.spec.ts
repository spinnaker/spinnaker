'use strict';

describe('Component: gceAutoHealingPolicySelector', function () {
  beforeEach(window.module(require('./autoHealingPolicySelector.component').GCE_AUTOHEALING_POLICY_SELECTOR));

  beforeEach(
    window.inject(function ($templateCache) {
      this.templateUrl = require('./autoHealingPolicySelector.component.html');
      this.html = $templateCache.get(this.templateUrl);
    }),
  );

  it('template does not include a maxUnavailable control', function () {
    expect(this.html).toEqual(jasmine.any(String));
    expect(this.html).toContain('Initial Delay');
    expect(this.html).not.toContain('maxUnavailable');
    expect(this.html).not.toContain('Max Unavailable');
  });
});

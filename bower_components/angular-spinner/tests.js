/* License: MIT.
 * Copyright (C) 2013, 2014, Uri Shaked and contributors.
 */

'use strict';

describe('Directive: us-spinner', function () {
	var Spinner;

	beforeEach(module(function ($provide) {
		Spinner = jasmine.createSpy('Spinner');
		Spinner.prototype.spin = jasmine.createSpy('Spinner.spin');
		Spinner.prototype.stop = jasmine.createSpy('Spinner.stop');

		$provide.value('$window', {
			location: angular.copy(window.location),
			navigator: angular.copy(window.navigator),
			document: window.document,
			Spinner: Spinner
		});
	}));

	beforeEach(module('angularSpinner'));

	it('should create a spinner object', inject(function ($rootScope, $compile) {
		var element = angular.element('<div us-spinner></div>');
		element = $compile(element)($rootScope);
		$rootScope.$digest();
		expect(Spinner).toHaveBeenCalled();
	}));

	it('should start spinning the spinner automatically', inject(function ($rootScope, $compile) {
		var element = angular.element('<div us-spinner></div>');
		element = $compile(element)($rootScope);
		$rootScope.$digest();
		expect(Spinner.prototype.spin).toHaveBeenCalled();
		expect(Spinner.prototype.stop).not.toHaveBeenCalled();
	}));

	it('should start spinning the second spinner without stopping the first one', inject(function ($rootScope, $compile) {
		var element = angular.element('<div us-spinner></div>');
		element = $compile(element)($rootScope);
		var secondElement = angular.element('<div us-spinner></div>');
		secondElement = $compile(secondElement)($rootScope);
		$rootScope.$digest();
		expect(Spinner.prototype.spin.calls.count()).toBe(2);
		expect(Spinner.prototype.stop).not.toHaveBeenCalled();
	}));

	it('should set spinner options as given in attribute', inject(function ($rootScope, $compile) {
		var element = angular.element('<div us-spinner="{width:15}"></div>');
		element = $compile(element)($rootScope);
		$rootScope.$digest();
		expect(Spinner).toHaveBeenCalledWith({width: 15});
	}));

	it('should update spinner options in response to scope updates', inject(function ($rootScope, $compile) {
		$rootScope.actualWidth = 25;
		var element = angular.element('<div us-spinner="{width:actualWidth}"></div>');
		element = $compile(element)($rootScope);
		$rootScope.$digest();
		expect(Spinner).toHaveBeenCalledWith({width: 25});
		expect(Spinner.prototype.stop).not.toHaveBeenCalled();

		$rootScope.actualWidth = 72;
		$rootScope.$digest();
		expect(Spinner).toHaveBeenCalledWith({width: 72});
		expect(Spinner.prototype.stop).toHaveBeenCalled();
		expect(Spinner.prototype.spin.calls.count()).toBe(2);
	}));

	it('should stop the spinner when the scope is destroyed', inject(function ($rootScope, $compile) {
		var scope = $rootScope.$new();
		var element = angular.element('<div us-spinner></div>');
		element = $compile(element)(scope);
		$rootScope.$digest();
		expect(Spinner.prototype.stop).not.toHaveBeenCalled();
		scope.$destroy();
		expect(Spinner.prototype.stop).toHaveBeenCalled();
	}));

	it('should not start spinning automatically', inject(function ($rootScope, $compile) {
		var element = angular.element('<div us-spinner spinner-key="spinner"></div>');
		element = $compile(element)($rootScope);
		$rootScope.$digest();
		expect(Spinner.prototype.spin).not.toHaveBeenCalled();
	}));

	it('should start spinning when service trigger the spin event', inject(function ($rootScope, $compile, usSpinnerService) {
		var element = angular.element('<div us-spinner spinner-key="spinner"></div>');
		element = $compile(element)($rootScope);
		$rootScope.$digest();
		expect(Spinner.prototype.spin).not.toHaveBeenCalled();
		usSpinnerService.spin('spinner');
		expect(Spinner.prototype.spin).toHaveBeenCalled();
	}));

	it('should start spinning the spinner automatically and stop when service trigger the stop event', inject(function ($rootScope, $compile, usSpinnerService) {
		var element = angular.element('<div us-spinner spinner-key="spinner" spinner-start-active="1"></div>');
		element = $compile(element)($rootScope);
		$rootScope.$digest();
		expect(Spinner.prototype.spin).toHaveBeenCalled();
		usSpinnerService.stop('spinner');
		expect(Spinner.prototype.stop).toHaveBeenCalled();
	}));

	it('should start spinning the second spinner without starting the first one', inject(function ($rootScope, $compile, usSpinnerService) {
		var element = angular.element('<div us-spinner spinner-key="spinner"></div>');
		element = $compile(element)($rootScope);
		var secondElement = angular.element('<div us-spinner spinner-key="spinner2"></div>');
		secondElement = $compile(secondElement)($rootScope);
		$rootScope.$digest();
		usSpinnerService.spin('spinner2');
		expect(Spinner.prototype.spin.calls.count()).toBe(1);
		expect(Spinner.prototype.stop).not.toHaveBeenCalled();
	}));

	it('should start spinning the spinners with the same key', inject(function ($rootScope, $compile, usSpinnerService) {
		$compile('<div us-spinner spinner-key="spinner"></div>')($rootScope);
		$compile('<div us-spinner spinner-key="spinner2"></div>')($rootScope);
		$compile('<div us-spinner spinner-key="spinner"></div>')($rootScope);
		$compile('<div us-spinner spinner-key="spinner2"></div>')($rootScope);
		$compile('<div us-spinner spinner-key="spinner"></div>')($rootScope);
		$rootScope.$digest();
		usSpinnerService.spin('spinner');
		expect(Spinner.prototype.spin.calls.count()).toBe(3);
		expect(Spinner.prototype.stop).not.toHaveBeenCalled();
		usSpinnerService.stop('spinner');
		expect(Spinner.prototype.stop.calls.count()).toBe(3);
		usSpinnerService.spin('spinner2');
		expect(Spinner.prototype.spin.calls.count()).toBe(5);
		usSpinnerService.stop('spinner2');
		expect(Spinner.prototype.stop.calls.count()).toBe(5);
	}));
});

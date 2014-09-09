/* License: MIT.
 * Copyright (C) 2013, 2014, Uri Shaked and contributors.
 */

'use strict';

module.exports = function (config) {
	config.set({
		basePath: '',
		frameworks: ['jasmine'],
		logLevel: config.LOG_INFO,
		browsers: ['PhantomJS'],
		autoWatch: true,
		reporters: ['dots', 'coverage'],
		files: [
			'bower_components/angular/angular.js',
			'bower_components/angular-mocks/angular-mocks.js',
			'bower_components/spin.js/spin.js',
			'angular-spinner.js',
			'tests.js'
		],
		preprocessors: {
			'components/spin.js/spin.js': 'coverage',
			'angular-spinner.js': 'coverage'
		},
		coverageReporter: {
			type: 'lcov',
			dir: 'coverage/'
		}
	});
};

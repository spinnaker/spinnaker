# Changelog

## 0.5.1 - 2014-08-09
- AMD / Require.js compatibility ([#11](https://github.com/urish/angular-spinner/pull/11), contributed by [floribon](https://github.com/floribon))
- Bugfix: Stop events are ignored if sent before the directive is fully initialized and `startActive` is true ([#22](https://github.com/urish/angular-spinner/pull/22), contributed by [vleborgne](https://github.com/vleborgne))

## 0.5.0 - 2014-06-03

- Add support for expressions in attributes ([#12](https://github.com/urish/angular-spinner/pull/12), contributed by [aaronroberson](https://github.com/aaronroberson))
- Generate source map for the minified version ([#14](https://github.com/urish/angular-spinner/issues/14))
- Add a `main` field to package.json ([#15](https://github.com/urish/angular-spinner/pull/15), contributed by [elfreyshira](https://github.com/elfreyshira))
- Enable support for AngularJS 1.3.x in bower.json

## 0.4.0 - 2014-03-15

- Upgrade spin.js to 2.0.0. See breaking changes [here](http://fgnass.github.io/spin.js/#v2.0.0).

## 0.3.1 - 2014-01-31

- Fixed an issue that caused the minified code to fail.

## 0.3.0 - 2014-01-26

- Add ability to control spinner state with bundled service (([#6](https://github.com/urish/angular-spinner/pull/6), contributed by [lossendae](https://github.com/lossendae))

## 0.2.1 - 2013-08-28

- Add test coverage reporting
- Stop the spinner on scope destroy
- Support for AngularJS 1.2

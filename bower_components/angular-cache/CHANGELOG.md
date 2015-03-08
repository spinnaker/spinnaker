##### 3.2.5 02 February 2015

###### Backwards compatible bug fixes
- #152 - Expired items sometimes only expire after double time.
- #153 - Missing angular dependency in bower.json

##### 3.2.4 17 December 2014

###### Backwards compatible bug fixes
- #149 - when removing an object from localStorage the key didn't get removed if the passed parameter is of number type.

##### 3.2.3 13 December 2014

###### Backwards compatible bug fixes
- #112 - $resource cache and 3.0.0-beta-x
- #122 - Error using DSCacheFactory with $http/ $resource and localStorage
- #148 - Illegal operation when using local-/sessionStorage

##### 3.2.2 24 November 2014

###### Backwards compatible bug fixes
- #147 - `storeOnResolve` and `storeOnReject` should default to `false`

##### 3.2.1 10 November 2014

###### Backwards compatible bug fixes
- #142 - Use JSON.stringify instead of angular.toJson

##### 3.2.0 07 November 2014

###### Backwards compatible API changes
- #135 - Closes #135. (Improved handling of promises.)

##### 3.1.1 28 August 2014

###### Backwards compatible bug fixes
- #124 - DSCache.info does not work if the storageMode is localStorage.
- #127 - requirejs conflict, require object overwritten

##### 3.1.0 15 July 2014

###### Backwards compatible API changes
- #117 - call to DSCacheFactory(...) produces JSHint warning (Added DSCacheFactory.createCache method)

###### Backwards compatible bug fixes
- #118 - dist/angular-cache.js doesn't end with a semicolon (Upgraded dependencies)
- #120 - How come the non minified version has minified code? (Upgraded dependencies)

##### 3.0.3 16 June 2014

###### Backwards compatible bug fixes
- Angular 1.2.18 with $http/localStorage #116

##### 3.0.2 15 June 2014

###### Backwards compatible bug fixes
- $http w/ cache is trying to store a promise, which dies on JSON.stringify #115

##### 3.0.1 14 June 2014

###### Backwards compatible bug fixes
- Added polyfill for `$$minErr`.

##### 3.0.0 14 June 2014

3.0.0 Release

##### 3.0.0-beta.4 22 April 2014

###### Backwards compatible API changes
- Add feature to 'touch' elements in the cache #103

###### Backwards compatible bug fixes
- `localstorage` and Safari Private Browsing #107

##### 3.0.0-beta.3 03 March 2014

###### Backwards compatible bug fixes
- Fixed duplicate keys when using localStorage #106

##### 3.0.0-beta.2 25 February 2014

###### Backwards compatible bug fixes
- Fixed missing reference to DSBinaryHeap #105

##### 3.0.0-beta.1 24 February 2014

###### Breaking API changes
- `maxAge` and `deleteOnExpire` are no longer overridable for individual items
- Renamed angular module to `angular-data.DSCacheFactory`. Angular-cache is now part of the `angular-data` namespace
- The `verifyIntegrity` option has been completely removed due to a cache being exclusively in-memory OR in web storage #96
- Supported values for the `storageMode` option are now: `"memory"`, `"localStorage"` or `"sessionStorage"` with the default being `"memory"`
- `DSCache#put(key, value)` no longer accepts a third `options` argument
- `DSCache#removeExpired()` no longer accepts an `options` argument and thus no longer supports returning removed expired items as an array
- `DSCache#remove(key)` no longer accepts an `options` argument
- `DSCache#setOptions(options[, strict])` no longer accepts `storageMode` and `storageImpl` as part of the `options` argument
- `storageMode` is no longer dynamically configurable
- `storageImpl` is no longer dynamically configurable

###### Backwards compatible API changes
- Added `DSCache#enable()`
- Added `DSCache#disable()`
- Added `DSCache#setCapacity(capacity)`
- Added `DSCache#setMaxAge(maxAge)`
- Added `DSCache#setCacheFlushInterval(cacheFlushInterval)`
- Added `DSCache#setRecycleFreq(recycleFreq)`
- Added `DSCache#setDeleteOnExpire(deleteOnExpire)`
- Added `DSCache#setOnExpire(onExpire)`
- Added option `storagePrefix` for customizing the prefix used in `localStorage`, etc. #98
- Refactored to be in-memory OR webStorage, never both #96

###### Other
- I might have missed something...

##### 2.3.3 - 24 February 2014

###### Backwards compatible bug fixes
- *sigh Fixed #102 (regression from #100)

##### 2.3.2 - 23 February 2014

###### Backwards compatible bug fixes
- Fixed #100 (regression from #89)

##### 2.3.1 - 19 February 2014

###### Backwards compatible bug fixes
- Fixed #89

##### 2.3.0 - 09 January 2014
- Caches can now be disabled #82
- The `options` object (`$angularCacheFactory()`, `AngularCache#setOptions()`, and `$angularCacheFactoryProvider.setCacheDefaults()`) now accepts a `disabled` field, which can be set to `true` and defaults to `false`.
- `$angularCacheFactory.enableAll()` will enable any disabled caches.
- `$angularCacheFactory.disableAll()` will disable all caches.
- A disabled cache will operate as normal, except `AngularCache#get()` and `AngularCache#put()` will both immediately return `undefined` instead of performing their normal functions.

###### Backwards compatible API changes
- `removeExpired()` now returns an object (or array) of the removed items.

###### Backwards compatible bug fixes
- `removeExpired()` now removes _all_ expired items.

##### 2.2.0 - 15 December 2013

###### Backwards compatible API changes
- `removeExpired()` now returns an object (or array) of the removed items.

###### Backwards compatible bug fixes
- `removeExpired()` now removes _all_ expired items.

##### 2.1.1 - 20 November 2013

###### Backwards compatible bug fixes
- Allow number keys, but stringify them #76
- Fix "Uncaught TypeError: Cannot read property 'maxAge' of null" #77 (thanks @evngeny-o)

##### 2.1.0 - 03 November 2013

###### Backwards compatible API changes
- Modify .get(key, options) to accept multiple keys #71 (thanks @roryf)

###### Other
- Run tests against multiple versions of Angular.js #72
- Add banner to dist/angular-cache.min.js #68

##### 2.0.0 - 30 October 2013
- Not all methods of AngularCache and $angularCacheFactory are in README #61
- Fix demo to work with 2.0.0-rc.1 #62
- Using Bower to install this package, the dist filenames change per version? #63

##### 2.0.0-rc.1 - 14 October 2013

###### Breaking API changes
- Swapped `aggressiveDelete` option for `deleteOnExpire` option. #30, #47
- Changed `$angularCacheFactory.info()` to return an object similar to `AngularCache.info()` #45
- Namespaced angular-cache module under `jmdobry` so it is now "jmdobry.angular-cache". #42
- Substituted `storageImpl` and `sessionStorageImpl` options for just `storageImpl` option.

###### Backwards compatible API changes
- Added `recycleFreq` to specify how frequently to check for expired items (no more $timeout). #28, #57
- Added ability to set global cache defaults in $angularCacheFactoryProvider. #55

###### Backwards compatible bug fixes
- cacheFlushInterval doesn't clear web storage when storageMode is used. #52
- AngularCache#info(key) should return 'undefined' if the key isn't in the cache #53
- Fixed timespan issues in README.md. #59

###### Other
- Refactored angular-cache `setOptions()` internals to be less convoluted and to have better validation. #46
- Re-wrote documentation to be clearer and more organized. #56
- Fixed documentation where time spans were incorrectly labeled. #59

##### 1.2.0 - 20 September 2013

###### Backwards compatible API changes
- Added AngularCache#info(key) #43

###### Backwards compatible bug fixes
- Fixed #39, #44, #49, #50

##### 1.1.0 - 03 September 2013

###### Backwards compatible API changes
- Added `onExpire` callback hook #27
- Added `$angularCacheFactory.removeAll()` and `$angularCacheFactory.clearAll()` convenience methods #37, #38

###### Backwards compatible bug fixes
- Fixed #36

##### 1.0.0 - 25 August 2013
- Closed #31 (Improved documentation)
- Closed #32

##### 1.0.0-rc.1 - 21 August 2013
- Added localStorage feature #26, #29

##### 0.9.1 - 03 August 2013
- Fixed #25

##### 0.9.0 - 03 August 2013
- Added a changelog #13
- Added documentation for installing with bower
- Added ability to set option `aggressiveDelete` when creating cache and when adding items
- Cleaned up README.md
- Switched the demo to use Bootstrap 3

##### 0.8.2 - 09 July 2013
- Added CONTRIBUTING.md #22
- Cleaned up meta data in bower.json and package.json

##### 0.8.1 - 09 July 2013
- Added .jshintrc
- Cleaned up the docs a bit
- `bower.json` now uses `src/angular-cache.js` instead of the versioned output files #21
- From now on the tags for the project will be named using [semver](http://semver.org/)

##### 0.8.0 - 08 July 2013
- Added `AngularCache.setOptions()`, the ability to dynamically change the configuration of a cache #20
- Added `AngularCache.keys()`, which returns an array of the keys in a cache #19
- Added `AngularCache.keySet()`, which returns a hash of the keys in a cache #19

##### 0.7.2 - June 2013
- Added `angular-cache` to bower registry #7
- Created a working demo #9 #17
- Fixed the size not being reset to 0 when the cache clears itself #14 #16
- Added `$angularCacheFactory.keys()`, which returns an array of the keys (the names of the caches) in $angularCacheFactory #18
- Added `$angularCacheFactory.keySet()`, which returns a hash of the keys (the names of the caches) in $angularCacheFactory #18

##### 0.6.1 - June 2013
- Got the project building on TravisCI
- Renamed the project to `angular-cache` #5

##### 0.5.0 - June 2013
- Added a roadmap to README.md #4
- Clarify usage documentation #3
- Wrote unit tests #2

##### 0.4.0 - May 2013
- Added Grunt build tasks #1

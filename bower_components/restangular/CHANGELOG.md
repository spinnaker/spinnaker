# New release information
New releases are in Releases section of Github
https://github.com/mgonto/restangular/releases

#1.0.11
* Documentation Typo fixes
* errorInterceptor can now stop Restangular from rejecting the promise
* Bugfix fot method override on DELETE. Now it works

#1.0.9
* **BREAKING CHANGE**: Restangular methods created with `addRestangularMethod` will change its signature depending on the opreation. If the operation is safe (GET, OPTIONS, etc.), the signature is methodName(params, headers, elemForBody). If it's not safe (POST, PUT, etc.), the signature is methodName(elemForBody, params, headers). This is to facilitate using them as when it's not safe, you're usually going to set a body
* Now you can configure default request parameters per method and for everything as well
* Added the ability to use Cannonical IDs. They're used if you need to change Primary Key (ID) of the element (Really weird case).
* If response is null or undefined, the element sent in the request ISN'T used anymore. This is to have clarity of what's returned by the server and also to fix one bug.
* Added tests
* Fixed bug with ID when it was an empty string
* Added missing ';'.


#1.0.7
* `baseUrl` can now be set either with or without ending `/` and it'll work

#1.0.5
* Several bug fixes
* Added `parentless` configuration to ignore nested restful URLs

#1.0.2
* First final release
* Added `one` and `all` to all collection methods
* Added `fullResponse` for getting the full `$http` response in every call
* Improved documentation on `addElemTransformer`
* Configuration can be set globally on either `RestangularProvider` or `Restangular`

#0.8.9
* Fix call to `isOverridenMethod` in `setMethodOverriders`.

#0.8.8
* Removed extra trailling slash for elements without ID. Thanks @cboden

#0.8.7
* Bugfix for Refactor

#0.8.6
* Ditched the buggy `$resource` and using `$http` inside :D

#0.8.4
* Fixed bug with defaultHttpFields for scoped configuration
* Added `defaultHeaders`

#0.8.3
* Fixed bug with URLHandler. Now it uses local configuration as well
* Added error interceptor
* Fixed minor bugs


#0.8.0
* Big refactor to use scoped configurations

#0.7.3
* All configuration can be done via either `Restangular` or `RestangularProvider`
* url field now is called getRestangularUrl
* `id` configuration from `restangularFields` now accepts nested (dotted) properties

#0.7.1
* Added `defaultRequestParams` to set default request query parameters

#0.7.0
* RequestInterceptor wasn't being called in getList
* Removed extra `/` when no restangularWhat is provided. This is fixed by Angular's team since version 1.1.5 but this fixes it for all versions including 1.0.X
* Added documentation for supported AngularJS versions
* Added url method to elements which returns the URL of the current object

# 0.6.9
* Wrapping everything in an anonymous self executed function to not expose anything

# 0.6.7
* Bug fix for a regresion error using _.omit
* Added element transformers to transform any Restangularized element.
* Added putElement method to collection to put a certain element at an index and return a promise of the updated array.

# 0.6.5
* Added `Restangular.copy` for copying objects

# 0.6.4
* added methodOverriders to override any HTTP Method
* Added requestInterceptor

# 0.6.3
* Added `defaultHttpFields` configuration property

# 0.6.2
* URL suffix is unescaped now

# 0.6.1
* Elements are striped from Restangular fields before being sent to the server

# 0.6.0
* Fixed bug when adding metadata to response in ResopnseExtractor. It wasn't being added
* Added enhanced promises. [Check the section in README](https://github.com/mgonto/restangular/blob/master/README.md#enhanced-promises).

# 0.5.5
* Changed by default from Underscore to Lodash. They both can be used anyway. (thanks @pauldijou)
* Added tests for both Underscore and Lodash to check it's working. (thanks @pauldijou)

# 0.5.4
* Added onElemRestangularized hook
* Added posibility to add your own Restangular methods

# 0.5.3
* Added the posibility to do URL Building and RequestLess tree navigations
* Added alias to `do[method]`. For example, Now you can do `customPOST` as well as `doPOST`

# 0.5.2
* responseExtractor renamed to responseInterceptor. Added alias from responseExtractor to responseInterceptor to mantain backwards compatibility
* responseExtractor now receives 4 parameters. Response, operation, what (path of current element) and URL
* Error function for any Restangular action now receives a response to get StatusCode and other interesting stuff

# 0.5.1
* Added listTypeIsArray property to set getList as not an array.

# 0.5.0
* Added `requestSuffix`configuration for requests ending en .json
* `what` field is now configurable and not hardcoded anymore
* All instance variables from `RestangularProvider` are now local variables to reduce visibility
* Fully functional version with all desired features

# 0.4.6
* Added Custom methods to all Restangular objects. Check it out in the README

# 0.4.5
* Fixed but that didn't let ID to be 0.
* Added different Collection methods and Element methods
* Added posibility po do a post in a collection to post an element to itself
* Added Travis CI for build
* Fixed bug with parentResource after a post of a new element
* When doing a post, if no element is returned, we enhance the object received as a parameter

# 0.3.4
* Added new HTTP methods to use: Patch, Head, Trace and Options (thanks @pauldijou)
* Added tests with Karma for all functionality.

# 0.3.3
* Restangular fields can now be configured. You can set the id, route and parentResource fields. They're not hardcoded anymore

# 0.3.2
* Added ResponseExtractor for when the real data is wrapped in an envelope in the WebServer response.

# 0.3.1

* Now all methods accept Headers. You can query `account.getList('buildings', {query: 'param'}, {'header': 'mine'})`

# 0.2.1

* Added query params to all methods. getList, post, put, get and delete accept query params now.

# 0.2.0
* Added post method to all elements. Now you can also create new elements by calling `account.post('buildings', {name: "gonto"})`. 

# 0.1.1
* Changed `elem.delete()` to `elem.remove()` due to errors with Closure Compiler in Play 2 

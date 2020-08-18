# \ExecutionsControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetLatestExecutionsByConfigIdsUsingGET**](ExecutionsControllerApi.md#GetLatestExecutionsByConfigIdsUsingGET) | **Get** /executions | Retrieves an ad-hoc collection of executions based on a number of user-supplied parameters. Either executionIds or pipelineConfigIds must be supplied in order to return any results. If both are supplied, an exception will be thrown.
[**SearchForPipelineExecutionsByTriggerUsingGET**](ExecutionsControllerApi.md#SearchForPipelineExecutionsByTriggerUsingGET) | **Get** /applications/{application}/executions/search | Search for pipeline executions using a combination of criteria. The returned list is sorted by buildTime (trigger time) in reverse order so that newer executions are first in the list.


# **GetLatestExecutionsByConfigIdsUsingGET**
> []interface{} GetLatestExecutionsByConfigIdsUsingGET(ctx, optional)
Retrieves an ad-hoc collection of executions based on a number of user-supplied parameters. Either executionIds or pipelineConfigIds must be supplied in order to return any results. If both are supplied, an exception will be thrown.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***ExecutionsControllerApiGetLatestExecutionsByConfigIdsUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ExecutionsControllerApiGetLatestExecutionsByConfigIdsUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **executionIds** | **optional.String**| A comma-separated list of executions to retrieve. Either this OR pipelineConfigIds must be supplied, but not both. | 
 **expand** | **optional.Bool**| Expands each execution object in the resulting list. If this value is missing, it is defaulted to true. | [default to true]
 **limit** | **optional.Int32**| The number of executions to return per pipeline configuration. Ignored if executionIds parameter is supplied. If this value is missing, it is defaulted to 1. | 
 **pipelineConfigIds** | **optional.String**| A comma-separated list of pipeline configuration IDs to retrieve recent executions for. Either this OR pipelineConfigIds must be supplied, but not both. | 
 **statuses** | **optional.String**| A comma-separated list of execution statuses to filter by. Ignored if executionIds parameter is supplied. If this value is missing, it is defaulted to all statuses. | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **SearchForPipelineExecutionsByTriggerUsingGET**
> []interface{} SearchForPipelineExecutionsByTriggerUsingGET(ctx, application, optional)
Search for pipeline executions using a combination of criteria. The returned list is sorted by buildTime (trigger time) in reverse order so that newer executions are first in the list.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| Only includes executions that are part of this application. If this value is \&quot;*\&quot;, results will include executions of all applications. | 
 **optional** | ***ExecutionsControllerApiSearchForPipelineExecutionsByTriggerUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ExecutionsControllerApiSearchForPipelineExecutionsByTriggerUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **eventId** | **optional.String**| Only includes executions that were triggered by a trigger with this eventId. | 
 **expand** | **optional.Bool**| Expands each execution object in the resulting list. If this value is missing, it is defaulted to false. | [default to false]
 **pipelineName** | **optional.String**| Only includes executions that with this pipeline name. | 
 **reverse** | **optional.Bool**| Reverses the resulting list before it is paginated. If this value is missing, it is defaulted to false. | [default to false]
 **size** | **optional.Int32**| Sets the size of the resulting list for pagination. This value must be &gt; 0. If this value is missing, it is defaulted to 10. | [default to 10]
 **startIndex** | **optional.Int32**| Sets the first item of the resulting list for pagination. The list is 0-indexed. This value must be &gt;&#x3D; 0. If this value is missing, it is defaulted to 0. | [default to 0]
 **statuses** | **optional.String**| Only includes executions with a status that is equal to a status provided in this field. The list of statuses should be given as a comma-delimited string. If this value is missing, includes executions of all statuses. Allowed statuses are: NOT_STARTED, RUNNING, PAUSED, SUSPENDED, SUCCEEDED, FAILED_CONTINUE, TERMINAL, CANCELED, REDIRECT, STOPPED, SKIPPED, BUFFERED. | 
 **trigger** | **optional.String**| Only includes executions that were triggered by a trigger that matches the subset of fields provided by this value. This value should be a base64-encoded string of a JSON representation of a trigger object. The comparison succeeds if the execution trigger contains all the fields of the input trigger, the fields are of the same type, and each value of the field \&quot;matches\&quot;. The term \&quot;matches\&quot; is specific for each field&#39;s type: - For Strings: A String value in the execution&#39;s trigger matches the input trigger&#39;s String value if the former equals the latter (case-insensitive) OR if the former matches the latter as a regular expression. - For Maps: A Map value in the execution&#39;s trigger matches the input trigger&#39;s Map value if the former contains all keys of the latter and their values match. - For Collections: A Collection value in the execution&#39;s trigger matches the input trigger&#39;s Collection value if the former has a unique element that matches each element of the latter. - Every other value is compared using the Java \&quot;equals\&quot; method (Groovy \&quot;&#x3D;&#x3D;\&quot; operator) | 
 **triggerTimeEndBoundary** | **optional.Int64**| Only includes executions that were built at or before the given time, represented as a Unix timestamp in ms (UTC). This value must be &lt;&#x3D; 9223372036854775807 (Long.MAX_VALUE) and &gt;&#x3D; the value of [triggerTimeStartBoundary], if provided. If this value is missing, it is defaulted to 9223372036854775807. | 
 **triggerTimeStartBoundary** | **optional.Int64**| Only includes executions that were built at or after the given time, represented as a Unix timestamp in ms (UTC). This value must be &gt;&#x3D; 0 and &lt;&#x3D; the value of [triggerTimeEndBoundary], if provided. If this value is missing, it is defaulted to 0. | [default to 0]
 **triggerTypes** | **optional.String**| Only includes executions that were triggered by a trigger with a type that is equal to a type provided in this field. The list of trigger types should be a comma-delimited string. If this value is missing, results will includes executions of all trigger types. | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


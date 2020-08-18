# \ApplicationControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**CancelPipelineUsingPUT**](ApplicationControllerApi.md#CancelPipelineUsingPUT) | **Put** /applications/{application}/pipelines/{id}/cancel | Cancel pipeline
[**CancelTaskUsingPUT**](ApplicationControllerApi.md#CancelTaskUsingPUT) | **Put** /applications/{application}/tasks/{id}/cancel | Cancel task
[**GetAllApplicationsUsingGET**](ApplicationControllerApi.md#GetAllApplicationsUsingGET) | **Get** /applications | Retrieve a list of applications
[**GetApplicationHistoryUsingGET**](ApplicationControllerApi.md#GetApplicationHistoryUsingGET) | **Get** /applications/{application}/history | Retrieve a list of an application&#39;s configuration revision history
[**GetApplicationUsingGET**](ApplicationControllerApi.md#GetApplicationUsingGET) | **Get** /applications/{application} | Retrieve an application&#39;s details
[**GetPipelineConfigUsingGET**](ApplicationControllerApi.md#GetPipelineConfigUsingGET) | **Get** /applications/{application}/pipelineConfigs/{pipelineName} | Retrieve a pipeline configuration
[**GetPipelineConfigsForApplicationUsingGET**](ApplicationControllerApi.md#GetPipelineConfigsForApplicationUsingGET) | **Get** /applications/{application}/pipelineConfigs | Retrieve a list of an application&#39;s pipeline configurations
[**GetPipelinesUsingGET**](ApplicationControllerApi.md#GetPipelinesUsingGET) | **Get** /applications/{application}/pipelines | Retrieve a list of an application&#39;s pipeline executions
[**GetStrategyConfigUsingGET**](ApplicationControllerApi.md#GetStrategyConfigUsingGET) | **Get** /applications/{application}/strategyConfigs/{strategyName} | Retrieve a pipeline strategy configuration
[**GetStrategyConfigsForApplicationUsingGET**](ApplicationControllerApi.md#GetStrategyConfigsForApplicationUsingGET) | **Get** /applications/{application}/strategyConfigs | Retrieve a list of an application&#39;s pipeline strategy configurations
[**GetTaskDetailsUsingGET**](ApplicationControllerApi.md#GetTaskDetailsUsingGET) | **Get** /applications/{application}/tasks/{id}/details/{taskDetailsId} | Get task details
[**GetTaskUsingGET**](ApplicationControllerApi.md#GetTaskUsingGET) | **Get** /applications/{application}/tasks/{id} | Get task
[**GetTasksUsingGET**](ApplicationControllerApi.md#GetTasksUsingGET) | **Get** /applications/{application}/tasks | Retrieve a list of an application&#39;s tasks
[**InvokePipelineConfigUsingPOST**](ApplicationControllerApi.md#InvokePipelineConfigUsingPOST) | **Post** /applications/{application}/pipelineConfigs/{pipelineName} | Invoke pipeline config
[**TaskUsingPOST**](ApplicationControllerApi.md#TaskUsingPOST) | **Post** /applications/{application}/tasks | Create task


# **CancelPipelineUsingPUT**
> map[string]interface{} CancelPipelineUsingPUT(ctx, id, optional)
Cancel pipeline

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 
 **optional** | ***ApplicationControllerApiCancelPipelineUsingPUTOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ApplicationControllerApiCancelPipelineUsingPUTOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **reason** | **optional.String**| reason | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **CancelTaskUsingPUT**
> map[string]interface{} CancelTaskUsingPUT(ctx, id)
Cancel task

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetAllApplicationsUsingGET**
> []interface{} GetAllApplicationsUsingGET(ctx, optional)
Retrieve a list of applications

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***ApplicationControllerApiGetAllApplicationsUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ApplicationControllerApiGetAllApplicationsUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **optional.String**| filters results to only include applications deployed in the specified account | 
 **owner** | **optional.String**| filters results to only include applications owned by the specified email | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetApplicationHistoryUsingGET**
> []interface{} GetApplicationHistoryUsingGET(ctx, application, optional)
Retrieve a list of an application's configuration revision history

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
 **optional** | ***ApplicationControllerApiGetApplicationHistoryUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ApplicationControllerApiGetApplicationHistoryUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **limit** | **optional.Int32**| limit | [default to 20]

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetApplicationUsingGET**
> map[string]interface{} GetApplicationUsingGET(ctx, application, optional)
Retrieve an application's details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
 **optional** | ***ApplicationControllerApiGetApplicationUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ApplicationControllerApiGetApplicationUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **expand** | **optional.Bool**| expand | [default to true]

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetPipelineConfigUsingGET**
> map[string]interface{} GetPipelineConfigUsingGET(ctx, application, pipelineName)
Retrieve a pipeline configuration

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **pipelineName** | **string**| pipelineName | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetPipelineConfigsForApplicationUsingGET**
> []interface{} GetPipelineConfigsForApplicationUsingGET(ctx, application)
Retrieve a list of an application's pipeline configurations

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetPipelinesUsingGET**
> []interface{} GetPipelinesUsingGET(ctx, application, optional)
Retrieve a list of an application's pipeline executions

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
 **optional** | ***ApplicationControllerApiGetPipelinesUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ApplicationControllerApiGetPipelinesUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **expand** | **optional.Bool**| expand | 
 **limit** | **optional.Int32**| limit | 
 **statuses** | **optional.String**| statuses | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetStrategyConfigUsingGET**
> map[string]interface{} GetStrategyConfigUsingGET(ctx, application, strategyName)
Retrieve a pipeline strategy configuration

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **strategyName** | **string**| strategyName | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetStrategyConfigsForApplicationUsingGET**
> []interface{} GetStrategyConfigsForApplicationUsingGET(ctx, application)
Retrieve a list of an application's pipeline strategy configurations

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetTaskDetailsUsingGET**
> map[string]interface{} GetTaskDetailsUsingGET(ctx, id, taskDetailsId, optional)
Get task details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 
  **taskDetailsId** | **string**| taskDetailsId | 
 **optional** | ***ApplicationControllerApiGetTaskDetailsUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ApplicationControllerApiGetTaskDetailsUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **xRateLimitApp** | **optional.String**| X-RateLimit-App | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetTaskUsingGET**
> map[string]interface{} GetTaskUsingGET(ctx, id)
Get task

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetTasksUsingGET**
> []interface{} GetTasksUsingGET(ctx, application, optional)
Retrieve a list of an application's tasks

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
 **optional** | ***ApplicationControllerApiGetTasksUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ApplicationControllerApiGetTasksUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **limit** | **optional.Int32**| limit | 
 **page** | **optional.Int32**| page | 
 **statuses** | **optional.String**| statuses | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **InvokePipelineConfigUsingPOST**
> HttpEntity InvokePipelineConfigUsingPOST(ctx, application, pipelineName, optional)
Invoke pipeline config

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **pipelineName** | **string**| pipelineName | 
 **optional** | ***ApplicationControllerApiInvokePipelineConfigUsingPOSTOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ApplicationControllerApiInvokePipelineConfigUsingPOSTOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **trigger** | [**optional.Interface of interface{}**](interface{}.md)| trigger | 
 **user** | **optional.String**| user | 

### Return type

[**HttpEntity**](HttpEntity.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **TaskUsingPOST**
> map[string]interface{} TaskUsingPOST(ctx, application, map_)
Create task

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **map_** | [**interface{}**](interface{}.md)| map | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


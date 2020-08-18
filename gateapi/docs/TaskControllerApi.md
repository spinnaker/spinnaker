# \TaskControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**CancelTaskUsingPUT1**](TaskControllerApi.md#CancelTaskUsingPUT1) | **Put** /tasks/{id}/cancel | Cancel task
[**CancelTasksUsingPUT**](TaskControllerApi.md#CancelTasksUsingPUT) | **Put** /tasks/cancel | Cancel tasks
[**DeleteTaskUsingDELETE**](TaskControllerApi.md#DeleteTaskUsingDELETE) | **Delete** /tasks/{id} | Delete task
[**GetTaskDetailsUsingGET1**](TaskControllerApi.md#GetTaskDetailsUsingGET1) | **Get** /tasks/{id}/details/{taskDetailsId} | Get task details
[**GetTaskUsingGET1**](TaskControllerApi.md#GetTaskUsingGET1) | **Get** /tasks/{id} | Get task
[**TaskUsingPOST1**](TaskControllerApi.md#TaskUsingPOST1) | **Post** /tasks | Create task


# **CancelTaskUsingPUT1**
> map[string]interface{} CancelTaskUsingPUT1(ctx, id)
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

# **CancelTasksUsingPUT**
> map[string]interface{} CancelTasksUsingPUT(ctx, ids)
Cancel tasks

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **ids** | [**[]string**](string.md)| ids | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DeleteTaskUsingDELETE**
> map[string]interface{} DeleteTaskUsingDELETE(ctx, id)
Delete task

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

# **GetTaskDetailsUsingGET1**
> map[string]interface{} GetTaskDetailsUsingGET1(ctx, id, taskDetailsId, optional)
Get task details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 
  **taskDetailsId** | **string**| taskDetailsId | 
 **optional** | ***TaskControllerApiGetTaskDetailsUsingGET1Opts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a TaskControllerApiGetTaskDetailsUsingGET1Opts struct

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

# **GetTaskUsingGET1**
> map[string]interface{} GetTaskUsingGET1(ctx, id)
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

# **TaskUsingPOST1**
> map[string]interface{} TaskUsingPOST1(ctx, map_)
Create task

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **map_** | [**interface{}**](interface{}.md)| map | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


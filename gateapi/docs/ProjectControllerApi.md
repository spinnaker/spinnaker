# \ProjectControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**AllPipelinesForProjectUsingGET**](ProjectControllerApi.md#AllPipelinesForProjectUsingGET) | **Get** /projects/{id}/pipelines | Get all pipelines for project
[**AllUsingGET3**](ProjectControllerApi.md#AllUsingGET3) | **Get** /projects | Get all projects
[**GetClustersUsingGET3**](ProjectControllerApi.md#GetClustersUsingGET3) | **Get** /projects/{id}/clusters | Get a project&#39;s clusters
[**GetUsingGET1**](ProjectControllerApi.md#GetUsingGET1) | **Get** /projects/{id} | Get a project


# **AllPipelinesForProjectUsingGET**
> []interface{} AllPipelinesForProjectUsingGET(ctx, id, optional)
Get all pipelines for project

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **id** | **string**| id | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **id** | **string**| id | 
 **limit** | **int32**| limit | [default to 5]
 **statuses** | **string**| statuses | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **AllUsingGET3**
> []interface{} AllUsingGET3(ctx, )
Get all projects

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetClustersUsingGET3**
> []interface{} GetClustersUsingGET3(ctx, id, optional)
Get a project's clusters

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **id** | **string**| id | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **id** | **string**| id | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetUsingGET1**
> map[string]interface{} GetUsingGET1(ctx, id)
Get a project

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **id** | **string**| id | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


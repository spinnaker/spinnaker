# \ProjectControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**AllPipelinesForProjectUsingGET**](ProjectControllerApi.md#AllPipelinesForProjectUsingGET) | **Get** /projects/{id}/pipelines | Get all pipelines for project


# **AllPipelinesForProjectUsingGET**
> []HashMap AllPipelinesForProjectUsingGET(ctx, id, optional)
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

[**[]HashMap**](HashMap.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


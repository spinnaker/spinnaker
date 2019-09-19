# \SearchControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**SearchUsingGET**](SearchControllerApi.md#SearchUsingGET) | **Get** /search | Search infrastructure


# **SearchUsingGET**
> []interface{} SearchUsingGET(ctx, type_, optional)
Search infrastructure

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **type_** | **string**| type | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **type_** | **string**| type | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **allowShortQuery** | **bool**| allowShortQuery | [default to false]
 **page** | **int32**| page | [default to 1]
 **pageSize** | **int32**| pageSize | [default to 10000]
 **platform** | **string**| platform | 
 **q** | **string**| q | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


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
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **type_** | **string**| type | 
 **optional** | ***SearchControllerApiSearchUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a SearchControllerApiSearchUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **xRateLimitApp** | **optional.String**| X-RateLimit-App | 
 **allowShortQuery** | **optional.Bool**| allowShortQuery | [default to false]
 **page** | **optional.Int32**| page | [default to 1]
 **pageSize** | **optional.Int32**| pageSize | [default to 10000]
 **platform** | **optional.String**| platform | 
 **q** | **optional.String**| q | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


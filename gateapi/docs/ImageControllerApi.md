# \ImageControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**FindImagesUsingGET**](ImageControllerApi.md#FindImagesUsingGET) | **Get** /images/find | Retrieve a list of images, filtered by cloud provider, region, and account
[**FindTagsUsingGET**](ImageControllerApi.md#FindTagsUsingGET) | **Get** /images/tags | Find tags
[**GetImageDetailsUsingGET**](ImageControllerApi.md#GetImageDetailsUsingGET) | **Get** /images/{account}/{region}/{imageId} | Get image details


# **FindImagesUsingGET**
> []interface{} FindImagesUsingGET(ctx, optional)
Retrieve a list of images, filtered by cloud provider, region, and account

The query parameter `q` filters the list of images by image name

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***ImageControllerApiFindImagesUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ImageControllerApiFindImagesUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **optional.String**| account | 
 **count** | **optional.Int32**| count | 
 **provider** | **optional.String**| provider | [default to aws]
 **q** | **optional.String**| q | 
 **region** | **optional.String**| region | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **FindTagsUsingGET**
> []interface{} FindTagsUsingGET(ctx, account, repository, optional)
Find tags

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **account** | **string**| account | 
  **repository** | **string**| repository | 
 **optional** | ***ImageControllerApiFindTagsUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ImageControllerApiFindTagsUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **xRateLimitApp** | **optional.String**| X-RateLimit-App | 
 **provider** | **optional.String**| provider | [default to aws]

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetImageDetailsUsingGET**
> []interface{} GetImageDetailsUsingGET(ctx, account, imageId, region, optional)
Get image details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **account** | **string**| account | 
  **imageId** | **string**| imageId | 
  **region** | **string**| region | 
 **optional** | ***ImageControllerApiGetImageDetailsUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a ImageControllerApiGetImageDetailsUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------



 **xRateLimitApp** | **optional.String**| X-RateLimit-App | 
 **provider** | **optional.String**| provider | [default to aws]

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


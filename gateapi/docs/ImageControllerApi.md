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
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **count** | **int32**| count | 
 **provider** | **string**| provider | [default to aws]
 **q** | **string**| q | 
 **region** | **string**| region | 

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
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
  **repository** | **string**| repository | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **repository** | **string**| repository | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **provider** | **string**| provider | [default to aws]

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
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
  **imageId** | **string**| imageId | 
  **region** | **string**| region | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **imageId** | **string**| imageId | 
 **region** | **string**| region | 
 **xRateLimitApp** | **string**| X-RateLimit-App | 
 **provider** | **string**| provider | [default to aws]

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


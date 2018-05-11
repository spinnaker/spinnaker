# \ImageControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**FindImagesUsingGET**](ImageControllerApi.md#FindImagesUsingGET) | **Get** /images/find | Retrieve a list of images, filtered by cloud provider, region, and account
[**FindTagsUsingGET**](ImageControllerApi.md#FindTagsUsingGET) | **Get** /images/tags | Find tags
[**GetImageDetailsUsingGET**](ImageControllerApi.md#GetImageDetailsUsingGET) | **Get** /images/{account}/{region}/{imageId} | Get image details


# **FindImagesUsingGET**
> []HashMap FindImagesUsingGET(ctx, optional)
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
 **provider** | **string**| provider | [default to aws]
 **q** | **string**| q | 
 **region** | **string**| region | 
 **account** | **string**| account | 
 **count** | **int32**| count | 

### Return type

[**[]HashMap**](HashMap.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
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
 **provider** | **string**| provider | [default to aws]
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetImageDetailsUsingGET**
> []HashMap GetImageDetailsUsingGET(ctx, account, region, imageId, optional)
Get image details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **account** | **string**| account | 
  **region** | **string**| region | 
  **imageId** | **string**| imageId | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **account** | **string**| account | 
 **region** | **string**| region | 
 **imageId** | **string**| imageId | 
 **provider** | **string**| provider | [default to aws]
 **xRateLimitApp** | **string**| X-RateLimit-App | 

### Return type

[**[]HashMap**](HashMap.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


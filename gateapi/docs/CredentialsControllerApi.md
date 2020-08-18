# \CredentialsControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetAccountUsingGET**](CredentialsControllerApi.md#GetAccountUsingGET) | **Get** /credentials/{account} | Retrieve an account&#39;s details
[**GetAccountsUsingGET**](CredentialsControllerApi.md#GetAccountsUsingGET) | **Get** /credentials | Retrieve a list of accounts


# **GetAccountUsingGET**
> AccountDetails GetAccountUsingGET(ctx, account, optional)
Retrieve an account's details

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **account** | **string**| account | 
 **optional** | ***CredentialsControllerApiGetAccountUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a CredentialsControllerApiGetAccountUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **xRateLimitApp** | **optional.String**| X-RateLimit-App | 
 **accountNonExpired** | **optional.Bool**|  | 
 **accountNonLocked** | **optional.Bool**|  | 
 **allowedAccounts** | [**optional.Interface of []string**](string.md)|  | 
 **authorities0Authority** | **optional.String**|  | 
 **credentialsNonExpired** | **optional.Bool**|  | 
 **email** | **optional.String**|  | 
 **enabled** | **optional.Bool**|  | 
 **firstName** | **optional.String**|  | 
 **lastName** | **optional.String**|  | 
 **password** | **optional.String**|  | 
 **roles** | [**optional.Interface of []string**](string.md)|  | 
 **username** | **optional.String**|  | 

### Return type

[**AccountDetails**](AccountDetails.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetAccountsUsingGET**
> []Account GetAccountsUsingGET(ctx, optional)
Retrieve a list of accounts

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***CredentialsControllerApiGetAccountsUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a CredentialsControllerApiGetAccountsUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **accountNonExpired** | **optional.Bool**|  | 
 **accountNonLocked** | **optional.Bool**|  | 
 **allowedAccounts** | [**optional.Interface of []string**](string.md)|  | 
 **authorities0Authority** | **optional.String**|  | 
 **credentialsNonExpired** | **optional.Bool**|  | 
 **email** | **optional.String**|  | 
 **enabled** | **optional.Bool**|  | 
 **expand** | **optional.Bool**| expand | 
 **firstName** | **optional.String**|  | 
 **lastName** | **optional.String**|  | 
 **password** | **optional.String**|  | 
 **roles** | [**optional.Interface of []string**](string.md)|  | 
 **username** | **optional.String**|  | 

### Return type

[**[]Account**](Account.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


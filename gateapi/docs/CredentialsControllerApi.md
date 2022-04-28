# \CredentialsControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**CreateAccountUsingPOST**](CredentialsControllerApi.md#CreateAccountUsingPOST) | **Post** /credentials | Creates a new account definition.
[**DeleteAccountUsingDELETE**](CredentialsControllerApi.md#DeleteAccountUsingDELETE) | **Delete** /credentials/{accountName} | Deletes an account definition by name.
[**GetAccountUsingGET**](CredentialsControllerApi.md#GetAccountUsingGET) | **Get** /credentials/{account} | Retrieve an account&#39;s details
[**GetAccountsByTypeUsingGET**](CredentialsControllerApi.md#GetAccountsByTypeUsingGET) | **Get** /credentials/type/{accountType} | Looks up account definitions by type.
[**GetAccountsUsingGET**](CredentialsControllerApi.md#GetAccountsUsingGET) | **Get** /credentials | Retrieve a list of accounts
[**UpdateAccountUsingPUT**](CredentialsControllerApi.md#UpdateAccountUsingPUT) | **Put** /credentials | Updates an existing account definition.


# **CreateAccountUsingPOST**
> AccountDefinition CreateAccountUsingPOST(ctx, optional)
Creates a new account definition.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***CredentialsControllerApiCreateAccountUsingPOSTOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a CredentialsControllerApiCreateAccountUsingPOSTOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **accountDefinition** | [**optional.Interface of AccountDefinition**](AccountDefinition.md)| Account definition body including a discriminator field named \&quot;type\&quot; with the account type. | 

### Return type

[**AccountDefinition**](AccountDefinition.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DeleteAccountUsingDELETE**
> DeleteAccountUsingDELETE(ctx, accountName)
Deletes an account definition by name.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **accountName** | **string**| Name of account definition to delete. | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

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

# **GetAccountsByTypeUsingGET**
> []AccountDefinition GetAccountsByTypeUsingGET(ctx, accountType, optional)
Looks up account definitions by type.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **accountType** | **string**| Value of the \&quot;@type\&quot; key for accounts to search for. | 
 **optional** | ***CredentialsControllerApiGetAccountsByTypeUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a CredentialsControllerApiGetAccountsByTypeUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **limit** | **optional.String**| Maximum number of entries to return in results. Used for pagination. | 
 **startingAccountName** | **optional.String**| Account name to start account definition listing from. Used for pagination. | 

### Return type

[**[]AccountDefinition**](AccountDefinition.md)

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

# **UpdateAccountUsingPUT**
> AccountDefinition UpdateAccountUsingPUT(ctx, optional)
Updates an existing account definition.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***CredentialsControllerApiUpdateAccountUsingPUTOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a CredentialsControllerApiUpdateAccountUsingPUTOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **accountDefinition** | [**optional.Interface of AccountDefinition**](AccountDefinition.md)| Account definition body including a discriminator field named \&quot;type\&quot; with the account type. | 

### Return type

[**AccountDefinition**](AccountDefinition.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)


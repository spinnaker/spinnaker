Spinnaker application <strong>${notification.source.application}</strong> was judged to <strong>stop</strong>.
<br/><br/>
<#if (notification.additionalContext.message)??>  ${notification.additionalContext.message}
<br/><br/>
</#if>For more details, please visit:
<br/><br/>
<a href="${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}">
${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}
</a>

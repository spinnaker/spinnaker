<#include "../manualJudgment/variables-email.ftl">
${stageDescription} <i>was judged to <b>stop</b></i> by ${notification.additionalContext.judgedBy}.

<#if (notification.additionalContext.message)??>
${markdownToHtml.convert(notification.additionalContext.message)}
</#if>

<#include "../manualJudgment/variables-slack.ftl">
${stageDescription} _was judged to *stop*_ by ${notification.additionalContext.judgedBy}.

<#if (notification.additionalContext.message)??>
${htmlToText.convert(notification.additionalContext.message)}
</#if>

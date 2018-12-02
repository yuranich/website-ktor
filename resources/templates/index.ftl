<#-- @ftlvariable name="data" type="edu.yuranich.IndexData" -->
<html>
    <head>
        <link rel="stylesheet" href="/static/styles.css">
    </head>
    <body>
        <img src="/static/ktor_logo.svg">
        <ul>
            <#list data.items as item>
                <li>${item}</li>
            </#list>
        </ul>
    </body>
</html>

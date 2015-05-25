# api-import-export

This is used to import and export APIs from WSO2 API Manager.

Main functionality of API Export is to retrieve all the required meta information and registry
resources for the requested API and generate a zipped archive.

Zipped archive consists of the following structure

    <APIName>-<version>
    |_ Meta Information
       |_ api.json
    |_ Documents
       |_ docs.json
    |_ Image
       |_ icon.<extension>
    |_ WSDL
       |_ <ApiName>-<version>.wsdl
    |_ Sequences
       |_ In Sequence
          |_<Sequence Name>.xml
       |_ Out Sequence
          |_<Sequence Name>.xml
       |_ Fault Sequence
          |_<Sequence Name>.xml

API Import accepts the exported zipped archive and create an API in the imported environment.

This feature has been implemented as a RESTful API.

##Sample cURL command for API export

curl -H "Authorization:Basic YWRtaW46YWRtaW4=" -X GET "https://10.100.7.39:9443/api-import-export/export-api?name=test&version=1.0.0&provider=admin"  -k > import.zip



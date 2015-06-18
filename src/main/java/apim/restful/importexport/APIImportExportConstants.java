/*
 *
 *  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */

package apim.restful.importexport;

/**
 * This class contains all the constants required for API Import and Export
 */
public final class APIImportExportConstants {

    //length of the name of the temporary
    public static final int TEMP_FILENAME_LENGTH = 5;
    //name of the uploaded zip file
    public static final String UPLOAD_FILE_NAME = "APIArchive.zip";
    //location of the api JSON file
    public static final String JSON_FILE_LOCATION = "/Meta-information/api.json";
    //location of the api swagger definition file
    public static final String SWAGGER_DEFINITION_LOCATION = "/Meta-information/swagger.json";
    //location of the image
    public static final String IMAGE_FILE_LOCATION = "/Image/";
    //name of the image
    public static final String IMAGE_FILE_NAME = "icon";
    //location of the documents JSON file
    public static final String DOCUMENT_FILE_LOCATION = "/Docs/docs.json";
    //name of the inline file type
    public static final String INLINE_DOC_TYPE = "INLINE";
    //name of the url file type
    public static final String URL_DOC_TYPE = "URL";
    //name of the physical file type
    public static final String FILE_DOC_TYPE = "FILE";
    //location of the in sequence
    public static final String IN_SEQUENCE_LOCATION = "/Sequences/in-sequence/";
    //location of the out sequence
    public static final String OUT_SEQUENCE_LOCATION = "/Sequences/out-sequence/";
    //location of the fault sequence
    public static final String FAULT_SEQUENCE_LOCATION = "/Sequences/fault-sequence/";
    //extension of xml files
    public static final String XML_EXTENSION = ".xml";
    //location of the wsdl file
    public static final String WSDL_LOCATION = "/WSDL/";
    //extension of wsdl files
    public static final String WSDL_EXTENSION = ".wsdl";

}

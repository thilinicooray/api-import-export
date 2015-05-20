/*
 *
 *  Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import org.wso2.carbon.utils.CarbonUtils;

import java.io.File;

/**
 * This class contains all the constants required for API Import and Export
 */
public final class APIImportExportConstants {

	//This is where archive file get generated for exporting API
	public static final String BASE_ARCHIVE_PATH = CarbonUtils.getCarbonHome() + File.separator + "tmp" +
	                                               File.separator + "work" ;
    //name of the new folder
    public static final String CREATED_FOLDER = "/ImportDirectory/";
    //name of the provider
    public static final String PROVIDER_NAME = "admin";
    //name of the uploaded zip file
    public static final String UPLOAD_FILE_NAME = "APIArchive.zip";
    //location of the api JSON file
    public static final String JSON_FILE_LOCATION = "/Meta-information/api.json";
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
    //name of the in sequence
    public static final String IN_SEQUENCE_NAME = "log_in_message";
    //location of the in sequence
    public static final String IN_SEQUENCE_LOCATION = "/Sequences/in-sequence/log_in_message.xml";
    //name of the out sequence
    public static final String OUT_SEQUENCE_NAME = "log_out_message";
    //location of the out sequence
    public static final String OUT_SEQUENCE_LOCATION = "/Sequences/out-sequence/log_out_message.xml";
    //name of the fault sequence
    public static final String FAULT_SEQUENCE_NAME = "json_fault";
    //location of the fault sequence
    public static final String FAULT_SEQUENCE_LOCATION = "/Sequences/fault-sequence/json_fault.xml";

}

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


import com.google.gson.Gson;
import com.sun.jersey.multipart.FormDataParam;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.POST;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.io.InputStream;

import apim.restful.importexport.utils.APIExportUtil;
import apim.restful.importexport.utils.APIImportUtil;
import apim.restful.importexport.utils.ArchiveGeneratorUtil;
import apim.restful.importexport.utils.AuthenticatorUtil;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

/**
 * This class provides JAX-RS services for exporting and importing APIs.
 * These services provides functionality for exporting and importing single API at a time.
 */
@Path("/") public class APIService {

	private static final Log log = LogFactory.getLog(APIService.class);

	/**
	 * This service exports an API from API Manager for a given API ID
	 * Meta information, API icon, documentation, WSDL and sequences are exported
	 * This service generates a zipped archive which contains all the above mentioned resources
	 * for a given API
	 *
	 * @param name         Name of the API that needs to be exported
	 * @param version      Version of the API that needs to be exported
	 * @param providerName Provider name of the API that needs to be exported
	 * @return Zipped API as the response to the service call
	 */
	@GET
    @Path("/export-api")
    @Produces("application/zip")
    public Response exportAPI(@QueryParam("name") String name, @QueryParam("version") String version,
			@QueryParam("provider") String providerName, @Context HttpHeaders httpHeaders) {

		String userName;
		if (name == null || version == null || providerName == null) {
			log.error("Invalid API Information ");

			return Response.status(Response.Status.BAD_REQUEST).entity("Invalid API " +
			                                                           "Information" +
			                                                           " ")
			               .type(MediaType.APPLICATION_JSON).
							build();
		}
		log.info("Retrieving API for API-Id : " + name + "-" + version + "-" + providerName);
		APIIdentifier apiIdentifier;
		try {

			Response authorizationResponse = AuthenticatorUtil.authorizeUser(httpHeaders);
			if (!(Response.Status.OK.getStatusCode() == authorizationResponse.getStatus())) {
				return authorizationResponse;
			}

			userName = AuthenticatorUtil.getAuthenticatedUserName();
			//provider names with @ signs are only accepted
			String apiDomain = MultitenantUtils.getTenantDomain(providerName);
			String apiRequesterDomain = MultitenantUtils.getTenantDomain(userName);
			//Allows to export APIs created only in current tenant domain
			if (!apiDomain.equals(apiRequesterDomain)) {
				//not authorized to export requested API
				log.error("Not authorized to " +
				          "export API :" + name + "-" + version + "-" + providerName);
				return Response.status(Response.Status.FORBIDDEN).entity("Not authorized to " +
				                                                         "export API :" + name +
				                                                         "-" + version + "-" +
				                                                         providerName)
				               .type(MediaType.APPLICATION_JSON).
								build();
			}

			apiIdentifier =
					new APIIdentifier(APIUtil.replaceEmailDomain(providerName), name, version);

			//create temp location for storing API data to generate archive
            String currentDirectory = System.getProperty("java.io.tmpdir");
            String createdFolders = "/" + RandomStringUtils.
                    randomAlphanumeric(5) + "/";
            File importFolder = new File(currentDirectory + createdFolders);
            APIExportUtil.createDirectory(importFolder.getPath());
			String archiveBasePath = importFolder.toString();

			APIExportUtil.setArchiveBasePath(archiveBasePath);

			Response ApiResourceRetrievalResponse =
					APIExportUtil.retrieveApiToExport(apiIdentifier, userName);

			//Retrieve resources : thumbnail, meta information, wsdl, sequences and documents
			// available for the exporting API
			if (!(Response.Status.OK.getStatusCode() == ApiResourceRetrievalResponse.getStatus())) {
				return ApiResourceRetrievalResponse;
			}

            ArchiveGeneratorUtil.archiveDirectory(archiveBasePath);

			log.info("API" + name + "-" + version + " exported successfully");

			File file = new File(archiveBasePath + ".zip");
			Response.ResponseBuilder response = Response.ok(file);
			response.header("Content-Disposition", "attachment; filename=\"" + file.getName() +
			                                       "\"");
			return response.build();

		} catch (APIExportException e) {
			log.error("APIExportException occurred while exporting ", e);
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
			               .entity("Internal Server Error").type(MediaType.APPLICATION_JSON).
							build();
		}

    }

    /**
     * This is the service which is used to import an API. All relevant API data will be included upon the creation of
     * the API.
     *
     * @param uploadedInputStream input stream from the REST request
     * @return response indicating the status of the process
     */
    @POST
    @Path("/import-api")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importAPI(@FormDataParam("file") InputStream uploadedInputStream,
                              @Context HttpHeaders httpHeaders) {

        try {
            Response authorizationResponse = AuthenticatorUtil.authorizeUser(httpHeaders);

            if (!(Response.Status.OK.getStatusCode() == authorizationResponse.getStatus())) {
                return authorizationResponse;
            } else {
                try {
                    String currentUser = AuthenticatorUtil.getAuthenticatedUserName();
                    APIImportUtil.initializeProvider(currentUser);
                    String currentDirectory = System.getProperty("java.io.tmpdir");
                    String createdFolders = "/" + RandomStringUtils.
                            randomAlphanumeric(APIImportExportConstants.TEMP_FILENAME_LENGTH) + "/";
                    File importFolder = new File(currentDirectory + createdFolders);
                    boolean folderCreateStatus = importFolder.mkdirs();

                    if (folderCreateStatus) {
                        String uploadFileName = APIImportExportConstants.UPLOAD_FILE_NAME;
                        String absolutePath = currentDirectory + createdFolders;
                        APIImportUtil.transferFile(uploadedInputStream, uploadFileName, absolutePath);
                        String extractedFolderName = APIImportUtil.extractArchive(
                                new File(absolutePath + uploadFileName), absolutePath);
                        APIImportUtil.importAPI(absolutePath + extractedFolderName , currentUser);
                        importFolder.deleteOnExit();
                        return Response.status(Status.CREATED).build();
                    } else {
                        return Response.status(Status.BAD_REQUEST).build();
                    }
                } catch (APIImportException e) {
                    String errorDetail = new Gson().toJson(e.getErrorDescription());
                    return Response.serverError().entity(errorDetail).build();
                } catch (APIManagementException e) {
                    String errorDetail = new Gson().toJson(e.getMessage());
                    return Response.serverError().entity(errorDetail).build();
                }
            }
        } catch (APIExportException e) {
            return Response.status(Status.FORBIDDEN).entity("Not authorized to import API.").build();
        }
    }
}


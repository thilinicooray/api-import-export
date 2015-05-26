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

package apim.restful.importexport.utils;

import apim.restful.importexport.APIImportException;
import apim.restful.importexport.APIImportExportConstants;
import apim.restful.importexport.APIService;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.FaultGatewaysException;
import org.wso2.carbon.apimgt.api.model.*;
import org.wso2.carbon.apimgt.impl.APIManagerFactory;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;

import java.io.*;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.apache.commons.io.IOUtils.closeQuietly;


/**
 * This class provides the functions utilized to import an API from an API archive.
 */
public final class APIImportUtil {

    private static final Log log = LogFactory.getLog(APIService.class);
    static APIProvider provider;

    /**
     * This method initializes the Provider when there is a direct request to import an API
     *
     * @throws org.wso2.carbon.apimgt.api.APIManagementException if the provider cannot be initialized
     */
    public static void initializeProvider() throws APIManagementException {
        provider = APIManagerFactory.getInstance().getAPIProvider(APIImportExportConstants.PROVIDER_NAME);
    }


    /**
     * This method uploads a given file to specified location
     *
     * @param uploadedInputStream input stream of the file
     * @param newFileName         name of the file to be created
     * @param storageLocation     destination of the new file
     * @throws APIImportException if the file transfer fails
     */
    public static void transferFile(InputStream uploadedInputStream, String newFileName, String storageLocation)
            throws APIImportException {
        FileOutputStream outFileStream = null;
        try {
            outFileStream = new FileOutputStream(new File(storageLocation, newFileName));
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = uploadedInputStream.read(bytes)) != -1) {
                outFileStream.write(bytes, 0, read);
            }
        } catch (IOException e) {
            log.error("Error in transferring files.", e);
            throw new APIImportException("Error in transferring archive files. " + e.getMessage());
        } finally {
            closeQuietly(outFileStream);
        }
    }

    /**
     * This method decompresses API the archive
     *
     * @param sourceFile           the archive containing the API
     * @param destinationDirectory location of the archive to be extracted
     * @return the name of the extracted zip
     * @throws APIImportException if the decompressing fails
     */
    public static String unzipArchive(File sourceFile, File destinationDirectory) throws APIImportException {

        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;
        File destinationFile;
        ZipFile zipfile = null;
        String extractedFolder = null;

        try {
            zipfile = new ZipFile(sourceFile);
            Enumeration zipEntries = null;
            if (zipfile != null) {
                zipEntries = zipfile.entries();
            }
            if (zipEntries != null) {
                int index = 0;
                while (zipEntries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) zipEntries.nextElement();

                    if (entry.isDirectory()) {

                        //This index variable is used to get the extracted folder name; that is root directory
                        if (index == 0) {

                            //Get the folder name without the '/' character at the end
                            extractedFolder = entry.getName().substring(0, entry.getName().length() - 1);
                        }
                        index = -1;
                        new File(destinationDirectory, entry.getName()).mkdir();
                        continue;
                    }
                    inputStream = new BufferedInputStream(zipfile.getInputStream(entry));
                    destinationFile = new File(destinationDirectory, entry.getName());
                    fileOutputStream = new FileOutputStream(destinationFile);
                    copyStreams(inputStream, fileOutputStream);
                }
            }
        } catch (ZipException e) {
            log.error("Failed to decompress API archive files. ", e);
            throw new APIImportException("Failed to decompress API archive files. " + e.getMessage());
        } catch (IOException e) {
            log.error("I/O error while retrieving API files from the archive. ", e);
            throw new APIImportException("I/O error while retrieving API files from the archive. " + e.getMessage());
        } finally {
            closeQuietly(zipfile);
            closeQuietly(inputStream);
            closeQuietly(fileOutputStream);
        }
        return extractedFolder;
    }

	/**
	 * This method decompresses API the archive
	 *
	 * @param sourceFile  The archive containing the API
	 * @param destination location of the archive to be extracted
	 * @return Name of the extracted directory
	 * @throws APIImportException If the decompressing fails
	 */
	public static String extractArchive(File sourceFile, String destination)
			throws APIImportException {
		BufferedInputStream inputStream = null;
		FileOutputStream outputStream = null;
		String archiveName = null;
		try {
			ZipFile zip = new ZipFile(sourceFile);
			Enumeration zipFileEntries = zip.entries();
			int index = 0;

			// Process each entry
			while (zipFileEntries.hasMoreElements()) {
				// grab a zip file entry

				ZipEntry entry = (ZipEntry) zipFileEntries.nextElement();
				String currentEntry = entry.getName();

				if (index == 0) {
					archiveName = currentEntry.substring(0, currentEntry.indexOf('/'));
					--index;
				}

				File destinationFile = new File(destination, currentEntry);
				File destinationParent = destinationFile.getParentFile();

				// create the parent directory structure if needed
				destinationParent.mkdirs();

				if (!entry.isDirectory()) {
					inputStream = new BufferedInputStream(zip.getInputStream(entry));
					// write the current file to disk
					outputStream = new FileOutputStream(destinationFile);
					IOUtils.copy(inputStream, outputStream);
				}
			}

			return archiveName;

		} catch (IOException e) {
			log.error("Failed to extract archive file ", e);
			throw new APIImportException("Failed to extract archive file. " + e.getMessage());
		} finally {
			closeQuietly(inputStream);
			closeQuietly(outputStream);
		}

	}

    /**
     * This method copies data from input stream and writes to output stream
     *
     * @param inStream  the input stream of the file to be written
     * @param outStream the output stream of the file to be written
     * @throws APIImportException if the files are not copied correctly
     */

    private static void copyStreams(InputStream inStream, FileOutputStream outStream) throws APIImportException {
        int count;
        byte data[] = new byte[1024];
        try {
            while ((count = inStream.read(data, 0, 1024)) != -1) {
                outStream.write(data, 0, count);
            }
        } catch (IOException e) {
            log.error("Failed to copy API archive files. ", e);
            throw new APIImportException("Failed to copy API archive files. " + e.getMessage());
        }
    }

    /**
     * This method imports an API to the API store
     *
     * @param pathToArchive location of the extracted folder of the API
     * @throws APIImportException     if the tiers are not supported or if api.json is not found
     * @throws APIManagementException if the resource addition to API fails
     */
    public static void importAPI(String pathToArchive) throws APIManagementException, APIImportException {

        Gson gson = new Gson();
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;

        try {
            inputStream = new FileInputStream(pathToArchive + APIImportExportConstants.JSON_FILE_LOCATION);
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            API importedApi = gson.fromJson(bufferedReader, API.class);
            Set<Tier> allowedTiers = provider.getTiers();
            boolean isAllTiersAvailable = allowedTiers.containsAll(importedApi.getAvailableTiers());

            if(!isAllTiersAvailable){

                //If at least one unsupported tier is found, it should be removed before adding API
                Set<Tier> unsupportedTiersList = Sets.difference(importedApi.getAvailableTiers(), allowedTiers);
                for (Tier unsupportedTier : unsupportedTiersList) {

                    //Process is continued with a warning and only supported tiers are added to the importer API
                    log.warn("Tier name : " + unsupportedTier.getName() + " is not supported.");
                }

                //Remove the unsupported tiers before adding the API
                importedApi.removeAvailableTiers(unsupportedTiersList);
            }
            provider.addAPI(importedApi);
            addAPIImage(pathToArchive, importedApi);
            addAPIDocuments(pathToArchive, importedApi, gson);
            addAPISequences(pathToArchive, importedApi);

        } catch (FileNotFoundException e) {
            log.error("Failed to locate file : " + APIImportExportConstants.JSON_FILE_LOCATION, e);
            throw new APIImportException("Failed to locate API JSON file. " + e.getMessage());
        } finally {
            closeQuietly(inputStream);
            closeQuietly(bufferedReader);
        }
    }

    /**
     * This method adds the icon to the API which is to be displayed at the API store.
     *
     * @param pathToArchive location of the extracted folder of the API
     * @param importedApi   the imported API object
     * @throws APIManagementException by org.wso2.carbon.apimgt.api.APIManagementException
     */
    private static void addAPIImage(String pathToArchive, API importedApi) throws APIManagementException {

        //Adding image icon to the API if there is any
        File imageFolder = new File(pathToArchive + APIImportExportConstants.IMAGE_FILE_LOCATION);

        try {

            if (imageFolder.isDirectory() && imageFolder.listFiles() != null && imageFolder.listFiles().length > 0) {
                for (File imageFile : imageFolder.listFiles()) {
                    if (imageFile.getName().contains(APIImportExportConstants.IMAGE_FILE_NAME)) {
                        String fileExtension = FilenameUtils.getExtension(imageFile.getAbsolutePath());
                        FileInputStream inputStream = new FileInputStream(imageFile.getAbsolutePath());
                        Icon apiImage = new Icon(inputStream, fileExtension);
                        String imageRegistryLocation = provider.addIcon(imageFile.getAbsolutePath(), apiImage);
                        importedApi.setThumbnailUrl(APIUtil.prependTenantPrefix(imageRegistryLocation,
                                importedApi.getId().getProviderName()));
                        APIUtil.setResourcePermissions(importedApi.getId().getProviderName(), null, null,
                                imageRegistryLocation);
                        provider.updateAPI(importedApi);
                    }
                }
            }
        } catch (FileNotFoundException e) {

            //This is logged and process is continued because icon is optional for an API
            log.error("Icon for API is not found", e);
        } catch (FaultGatewaysException e) {
            log.error("Failed to update API after adding icon", e);
            throw new APIManagementException("Failed to update API after adding icon. " + e.getMessage());
        }
    }

    /**
     * This method adds the documents to the imported API
     *
     * @param pathToArchive location of the extracted folder of the API
     * @param importedApi   the imported API object
     * @param gson          object related to the API data
     * @throws org.wso2.carbon.apimgt.api.APIManagementException if the document addition fails
     */
    private static void addAPIDocuments(String pathToArchive, API importedApi, Gson gson) throws APIManagementException {
        String docFileLocation = pathToArchive + APIImportExportConstants.DOCUMENT_FILE_LOCATION;
        APIIdentifier apiIdentifier = importedApi.getId();

        try {
            if (checkFileExistence(docFileLocation)) {
                FileInputStream inputStream = new FileInputStream(docFileLocation);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                Documentation[] documentations = gson.fromJson(bufferedReader, Documentation[].class);

                //For each type of document, separate actions are preformed
                for (Documentation doc : documentations) {

                    if(APIImportExportConstants.INLINE_DOC_TYPE.equalsIgnoreCase(doc.getSourceType().toString())){
                        provider.addDocumentation(apiIdentifier, doc);
                        provider.addDocumentationContent(importedApi, doc.getName(), doc.getSummary());
                    } else if (APIImportExportConstants.URL_DOC_TYPE.equalsIgnoreCase(doc.getSourceType().toString())){
                        provider.addDocumentation(apiIdentifier, doc);
                    } else if (APIImportExportConstants.FILE_DOC_TYPE.equalsIgnoreCase(doc.getSourceType().toString())) {
                        inputStream = new FileInputStream(pathToArchive + doc.getFilePath());
                        String docExtension = FilenameUtils.getExtension(pathToArchive + doc.getFilePath());
                        Icon apiDocument = new Icon(inputStream, docExtension);
                        String visibleRolesList = importedApi.getVisibleRoles();
                        String[] visibleRoles = new String[0];
                        if (visibleRolesList != null) {
                            visibleRoles = visibleRolesList.split(",");
                        }
                        String filePathDoc = APIUtil.getDocumentationFilePath(apiIdentifier, doc.getName());
                        APIUtil.setResourcePermissions(importedApi.getId().getProviderName(),
                                importedApi.getVisibility(), visibleRoles, filePathDoc);
                        doc.setFilePath(provider.addIcon(filePathDoc, apiDocument));
                        provider.addDocumentation(apiIdentifier, doc);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            log.error("Failed to add Documentations to API.", e);
            throw new APIManagementException("Failed to add Documentations to API. " + e.getMessage());
        }
    }

    /**
     * This method adds API sequences to the imported API
     *
     * @param pathToArchive location of the extracted folder of the API
     * @param importedApi   the imported API object
     * @throws org.wso2.carbon.apimgt.api.APIManagementException if adding sequences to the API fails or if the gateway
     *          fails during the update
     */
    private static void addAPISequences(String pathToArchive, API importedApi) throws APIManagementException {
        String inSequenceFileLocation = pathToArchive + APIImportExportConstants.IN_SEQUENCE_LOCATION;

        try {
            //Adding in-sequence, if any
            if (checkFileExistence(inSequenceFileLocation)) {
                importedApi.setInSequence(APIImportExportConstants.IN_SEQUENCE_NAME);
            }

            //Adding out-sequence, if any
            String outSequenceFileLocation = pathToArchive + APIImportExportConstants.OUT_SEQUENCE_LOCATION;
            if (checkFileExistence(outSequenceFileLocation)) {
                importedApi.setOutSequence(APIImportExportConstants.OUT_SEQUENCE_NAME);
            }

            //Adding fault-sequence, if any
            String faultSequenceFileLocation = pathToArchive + APIImportExportConstants.FAULT_SEQUENCE_LOCATION;
            if (checkFileExistence(faultSequenceFileLocation)) {
                importedApi.setFaultSequence(APIImportExportConstants.FAULT_SEQUENCE_NAME);
            }
            provider.updateAPI(importedApi);
        } catch (FaultGatewaysException e) {
            log.error("Gateway fail while adding sequences. ", e);
            throw new APIManagementException("Gateway fail while adding sequences. " + e.getMessage());
        }
    }

    /**
     * This method checks whether a given file exists
     *
     * @param fileLocation location of the file
     * @return true if the file exists, false otherwise
     */
    private static boolean checkFileExistence(String fileLocation) {
        File testFile = new File(fileLocation);
        return testFile.exists();
    }
}

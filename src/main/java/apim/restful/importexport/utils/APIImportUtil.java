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

import apim.restful.importexport.APIExportException;
import apim.restful.importexport.APIImportExportConstants;
import apim.restful.importexport.APIImportException;
import apim.restful.importexport.APIService;

import com.google.common.collect.Sets;
import com.google.gson.Gson;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.FaultGatewaysException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.Documentation;
import org.wso2.carbon.apimgt.api.model.Icon;
import org.wso2.carbon.apimgt.api.model.Tier;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;

import org.wso2.carbon.registry.api.Registry;
import org.wso2.carbon.registry.core.Resource;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
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
     * @param currentUserName the current logged in user
     * @throws APIExportException if provider cannot be initialized
     */
    public static void initializeProvider(String currentUserName) throws APIExportException {
        provider = APIExportUtil.getProvider(currentUserName);
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
     * @param sourceFile  The archive containing the API
     * @param destination location of the archive to be extracted
     * @return Name of the extracted directory
     * @throws APIImportException If the decompressing fails
     */
    public static String extractArchive(File sourceFile, String destination) throws APIImportException {

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

                //This index variable is used to get the extracted folder name; that is root directory
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
     * This method imports an API to the API store
     *
     * @param pathToArchive location of the extracted folder of the API
     * @param currentUser the current logged in user
     * @throws APIImportException     if the tiers are not supported or if api.json is not found
     * @throws APIManagementException if the resource addition to API fails
     */
    public static void importAPI(String pathToArchive, String currentUser)
            throws APIManagementException, APIImportException {

        Gson gson = new Gson();
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;

        try {
            inputStream = new FileInputStream(pathToArchive + APIImportExportConstants.JSON_FILE_LOCATION);
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            API importedApi = gson.fromJson(bufferedReader, API.class);
            Set<Tier> allowedTiers = provider.getTiers();
            boolean isAllTiersAvailable = allowedTiers.containsAll(importedApi.getAvailableTiers());

            if (!isAllTiersAvailable) {

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
            addAPISequences(pathToArchive, importedApi, currentUser);
            addAPIWsdl(pathToArchive, importedApi, currentUser);
            addSwaggerDefinition(importedApi.getId(), pathToArchive);

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
     * @throws org.wso2.carbon.apimgt.api.APIManagementException if API update fails after adding the image
     */
    private static void addAPIImage(String pathToArchive, API importedApi) throws APIManagementException {

        //Adding image icon to the API if there is any
        File imageFolder = new File(pathToArchive + APIImportExportConstants.IMAGE_FILE_LOCATION);

        try {
            if (imageFolder.isDirectory() && imageFolder.listFiles() != null && imageFolder.listFiles().length > 0) {
                for (File imageFile : imageFolder.listFiles()) {
                    if (imageFile.getName().contains(APIImportExportConstants.IMAGE_FILE_NAME)) {
                        String mimeType = URLConnection.guessContentTypeFromName(imageFile.getName());
                        FileInputStream inputStream = new FileInputStream(imageFile.getAbsolutePath());
                        Icon apiImage = new Icon(inputStream, mimeType);
                        String thumbPath = APIUtil.getIconPath(importedApi.getId());
                        String thumbnailUrl = provider.addIcon(thumbPath, apiImage);
                        importedApi.setThumbnailUrl(APIUtil.prependTenantPrefix(thumbnailUrl,
                                importedApi.getId().getProviderName()));
                        APIUtil.setResourcePermissions(importedApi.getId().getProviderName(), null, null,
                                thumbPath);
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
    private static void addAPIDocuments(String pathToArchive, API importedApi, Gson gson)
            throws APIManagementException {

        String docFileLocation = pathToArchive + APIImportExportConstants.DOCUMENT_FILE_LOCATION;
        APIIdentifier apiIdentifier = importedApi.getId();

        try {
            if (checkFileExistence(docFileLocation)) {
                FileInputStream inputStream = new FileInputStream(docFileLocation);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                Documentation[] documentations = gson.fromJson(bufferedReader, Documentation[].class);

                //For each type of document, separate actions are preformed
                for (Documentation doc : documentations) {

                    if (APIImportExportConstants.INLINE_DOC_TYPE.equalsIgnoreCase(doc.getSourceType().toString())) {
                        provider.addDocumentation(apiIdentifier, doc);
                        provider.addDocumentationContent(importedApi, doc.getName(), doc.getSummary());
                    } else if (APIImportExportConstants.URL_DOC_TYPE.equalsIgnoreCase(doc.getSourceType().toString())) {
                        provider.addDocumentation(apiIdentifier, doc);
                    } else if (APIImportExportConstants.FILE_DOC_TYPE.
                            equalsIgnoreCase(doc.getSourceType().toString())) {
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
     * This method adds API sequences to the imported API. If the sequence is a newly defined one, it is added.
     *
     * @param pathToArchive location of the extracted folder of the API
     * @param importedApi   the imported API object
     * @param currentUser   current logged in user
     * @throws APIImportException if getting the registry instance fails
     *
     */
    private static void addAPISequences(String pathToArchive, API importedApi, String currentUser)
            throws APIImportException {

        try {
            Registry registry = APIExportUtil.getRegistry(currentUser);
            String inSequenceFileName = importedApi.getInSequence() + APIImportExportConstants.XML_EXTENSION;
            String inSequenceFileLocation = pathToArchive + APIImportExportConstants.IN_SEQUENCE_LOCATION
                    + inSequenceFileName;

            //Adding in-sequence, if any
            if (checkFileExistence(inSequenceFileLocation)) {
                addSequenceToRegistry(registry, APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN,
                        inSequenceFileName, inSequenceFileLocation);
            }

            String outSequenceFileName = importedApi.getOutSequence() + APIImportExportConstants.XML_EXTENSION;
            String outSequenceFileLocation = pathToArchive + APIImportExportConstants.OUT_SEQUENCE_LOCATION
                    + outSequenceFileName;

            //Adding out-sequence, if any
            if (checkFileExistence(outSequenceFileLocation)) {
                addSequenceToRegistry(registry, APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT,
                        outSequenceFileName, outSequenceFileLocation);
            }

            String faultSequenceFileName = importedApi.getFaultSequence() + APIImportExportConstants.XML_EXTENSION;
            String faultSequenceFileLocation = pathToArchive + APIImportExportConstants.FAULT_SEQUENCE_LOCATION
                    + faultSequenceFileName;

            //Adding fault-sequence, if any
            if (checkFileExistence(faultSequenceFileLocation)) {
                addSequenceToRegistry(registry, APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT,
                        faultSequenceFileName, faultSequenceFileLocation);
            }
        } catch (APIExportException e) {
            log.error("Failed to get the registry instance. ", e);
            throw new APIImportException("Failed to get the registry instance. " + e.getMessage());
        }
    }

    /**
     * This method adds the sequence files to the registry.
     *
     * @param registry the registry instance
     * @param customSequenceType type of the sequence
     * @param sequenceFileName name of the sequence
     * @param sequenceFileLocation location of the sequence file
     * @throws APIImportException if getting the registry instance fails
     *
     */
    private static void addSequenceToRegistry(Registry registry, String customSequenceType, String sequenceFileName,
                                              String sequenceFileLocation) throws APIImportException {

        String regResourcePath = APIConstants.API_CUSTOM_SEQUENCE_LOCATION + File.separator + customSequenceType
                + File.separator + sequenceFileName;
        InputStream inSeqStream = null;
        try {
            if (registry.resourceExists(regResourcePath)) {
                if (log.isDebugEnabled()) {
                    log.debug("Defined sequences have already been added to the registry");
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Adding defined sequences to the registry.");
                }
                File sequenceFile = new File(sequenceFileLocation);
                inSeqStream = new FileInputStream(sequenceFile);
                byte[] inSeqData = IOUtils.toByteArray(inSeqStream);
                Resource inSeqResource = (Resource) registry.newResource();
                inSeqResource.setContent(inSeqData);
                registry.put(regResourcePath, inSeqResource);
            }
        } catch (org.wso2.carbon.registry.api.RegistryException e) {
            log.error("Failed to add sequences into the registry. ", e);
            throw new APIImportException("Failed to add sequences into the registry. " + e.getMessage());
        } catch (IOException e) {
            log.error("I/O error while writing sequence data to the registry. ", e);
            throw new APIImportException("I/O error while writing sequence data to the registry. " + e.getMessage());
        } finally {
            closeQuietly(inSeqStream);
        }
    }

    /**
     * This method adds the WSDL to the registry, if there is a WSDL associated with the API
     *
     * @param pathToArchive location of the extracted folder of the API
     * @param importedApi the imported API object
     * @param currentUser   current logged in user
     * @throws APIImportException if there is a URL error or registry error while storing the resource in registry
     */
    private static void addAPIWsdl(String pathToArchive, API importedApi, String currentUser)
            throws APIImportException {

        String wsdlFileName = importedApi.getId().getApiName() + "-" + importedApi.getId().getVersion() +
                APIImportExportConstants.WSDL_EXTENSION;
        String wsdlPath = pathToArchive + APIImportExportConstants.WSDL_LOCATION + wsdlFileName;

        if (checkFileExistence(wsdlPath)) {
            try {
                URL wsdlFileUrl = new File(wsdlPath).toURI().toURL();
                importedApi.setWsdlUrl(wsdlFileUrl.toString());
                Registry registry = APIExportUtil.getRegistry(currentUser);
                APIUtil.createWSDL((org.wso2.carbon.registry.core.Registry) registry, importedApi);
            } catch (MalformedURLException e) {
                log.error("Error in getting WSDL URL. ", e);
                throw new APIImportException("Error in getting WSDL URL. " + e.getMessage());
            } catch (APIExportException e) {
                log.error("Error in getting the registry instance to add WSDL. ", e);
                throw new APIImportException("Error in getting the registry instance to add WSDL. " + e.getMessage());
            } catch (org.wso2.carbon.registry.core.exceptions.RegistryException e) {
                log.error("Error in putting the WSDL resource to registry. ", e);
                throw new APIImportException("Error in putting the WSDL resource to registry. " + e.getMessage());
            } catch (APIManagementException e) {
                log.error("Error in creating the WSDL resource in the registry. ", e);
                throw new APIImportException("Error in creating the WSDL resource in the registry. " + e.getMessage());
            }
        }
    }

    /**
     * This method adds Swagger API definition to registry
     *
     * @param apiId Identifier of the imported API
     * @param archivePath File path where API archive stored
     * @throws APIImportException if there is an error occurs when adding Swagger definition
     */
    private static void addSwaggerDefinition(APIIdentifier apiId, String archivePath)
            throws APIImportException {
        try {
            String swaggerContent = FileUtils.readFileToString(
                    new File (archivePath + APIImportExportConstants.SWAGGER_DEFINITION_LOCATION));
            provider.saveSwagger20Definition(apiId, swaggerContent);
        } catch (APIManagementException e) {
            log.error("Error in adding Swagger definition for the API. ", e);
            throw new APIImportException("Error in adding Swagger definition for the API. " +
                    e.getMessage());
        } catch (IOException e) {
            log.error("Error in importing Swagger definition for the API. ", e);
            throw new APIImportException("Error in importing Swagger definition for the API. " +
                    e.getMessage());
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




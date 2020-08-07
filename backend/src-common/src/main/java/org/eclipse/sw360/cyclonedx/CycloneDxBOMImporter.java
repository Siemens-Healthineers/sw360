/*
 * Copyright Siemens Healthineers GmBH, 2023. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.cyclonedx;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.ExternalReference;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.parsers.JsonParser;
import org.cyclonedx.parsers.Parser;
import org.cyclonedx.parsers.XmlParser;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.common.ThriftEnumUtils;
import org.eclipse.sw360.datahandler.db.ComponentDatabaseHandler;
import org.eclipse.sw360.datahandler.db.PackageDatabaseHandler;
import org.eclipse.sw360.datahandler.db.ProjectDatabaseHandler;
import org.eclipse.sw360.datahandler.db.VendorRepository;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestStatus;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestSummary;
import org.eclipse.sw360.datahandler.thrift.MainlineState;
import org.eclipse.sw360.datahandler.thrift.ProjectReleaseRelationship;
import org.eclipse.sw360.datahandler.thrift.ReleaseRelationship;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.RequestSummary;
import org.eclipse.sw360.datahandler.thrift.SW360Exception;
import org.eclipse.sw360.datahandler.thrift.Visibility;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentContent;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentType;
import org.eclipse.sw360.datahandler.thrift.attachments.CheckStatus;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.components.ComponentType;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.packages.Package;
import org.eclipse.sw360.datahandler.thrift.packages.PackageManagerType;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectType;
import org.eclipse.sw360.datahandler.thrift.users.User;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.github.jsonldjava.shaded.com.google.common.io.Files;
import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import com.google.gson.Gson;

/**
 * CycloneDX BOM import implementation.
 * Supports both XML and JSON format of CycloneDX SBOM
 *
 * @author abdul.kapti@siemens-healthineers.com
 */
public class CycloneDxBOMImporter {
    private static final Logger log = LogManager.getLogger(CycloneDxBOMImporter.class);
    private static final String SCHEMA_PATTERN = ".+://(\\w*(?:[\\-@.\\\\s,_:/][/(.\\-)A-Za-z0-9]+)*)";
    private static final String DOT_GIT = ".git";
    private static final String SLASH = "/";
    private static final String DOT = ".";
    private static final String HASH = "#";
    private static final String JOINER = "||";
    private static final String XML_FILE_EXTENSION = "xml";
    private static final String JSON_FILE_EXTENSION = "json";
    private static final Pattern THIRD_SLASH_PATTERN = Pattern.compile("[^/]*(/[^/]*){2}");
    private static final Pattern FIRST_SLASH_PATTERN = Pattern.compile("/(.*)");
    private static final String VCS_HTTP_REGEX = "^[^:]+://";
    private static final String CLEAN_PUBLISHER_REGEX = "<[^>]+>";
    private static final String HTTPS_SCHEME = "https://";
    private static final String COMP_CREATION_COUNT_KEY = "compCreationCount";
    private static final String COMP_REUSE_COUNT_KEY = "compReuseCount";
    private static final String REL_CREATION_COUNT_KEY = "relCreationCount";
    private static final String REL_REUSE_COUNT_KEY = "relReuseCount";
    private static final String PKG_CREATION_COUNT_KEY = "pkgCreationCount";
    private static final String PKG_REUSE_COUNT_KEY = "pkgReuseCount";
    private static final String DEFAULT_CATEGORY = "Default_Category";

    private final ProjectDatabaseHandler projectDatabaseHandler;
    private final ComponentDatabaseHandler componentDatabaseHandler;
    private final PackageDatabaseHandler packageDatabaseHandler;
    private final VendorRepository vendorRepository;
    private final User user;

    public CycloneDxBOMImporter(ProjectDatabaseHandler projectDatabaseHandler,
            ComponentDatabaseHandler componentDatabaseHandler, PackageDatabaseHandler packageDatabaseHandler,
            VendorRepository vendorRepository, User user) {
        this.projectDatabaseHandler = projectDatabaseHandler;
        this.componentDatabaseHandler = componentDatabaseHandler;
        this.packageDatabaseHandler = packageDatabaseHandler;
        this.vendorRepository = vendorRepository;
        this.user = user;
    }

    /**
     * Creating the Map of Sanitized VCS URLs to List of Component -> Grouping by VCS URLs
     * Sanitizing VCS URLs:
     *      git+https://github.com/microsoft/ApplicationInsights-JS.git/tree/master/shared/AppInsightsCommon 
     *              --> microsoft.applicationinsights-js
     * @param components
     * @return Map<String, List<org.cyclonedx.model.Component>>
     */
    private Map<String, List<org.cyclonedx.model.Component>> getVcsToomponentMap(List<org.cyclonedx.model.Component> components) {
        return components.stream().filter(Objects::nonNull)
                .flatMap(comp -> CommonUtils.nullToEmptyList(comp.getExternalReferences()).stream()
                        .filter(Objects::nonNull)
                        .filter(ref -> ExternalReference.Type.VCS.equals(ref.getType()))
                        .map(ExternalReference::getUrl)
                        .map(String::toLowerCase)
                        .map(url -> url.replaceAll(SCHEMA_PATTERN, "$1"))
                        .map(url -> THIRD_SLASH_PATTERN.matcher(url))
                        .filter(matcher -> matcher.find())
                        .map(matcher -> matcher.group())
                        .map(url -> FIRST_SLASH_PATTERN.matcher(url))
                        .filter(matcher -> matcher.find())
                        .map(matcher -> matcher.group(1))
                        .map(url -> StringUtils.substringBefore(url, HASH))
                        .map(url -> StringUtils.removeEnd(url, DOT_GIT))
                        .map(url -> url.replaceAll(SLASH, DOT))
                        .map(url -> new AbstractMap.SimpleEntry<>(url, comp)))
                .collect(Collectors.groupingBy(e -> e.getKey(),
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    @SuppressWarnings("unchecked")
    public RequestSummary importFromBOM(InputStream inputStream, AttachmentContent attachmentContent, String projectId, User user) {
        RequestSummary requestSummary = new RequestSummary();
        Map<String, String> messageMap = new HashMap<>();
        requestSummary.setRequestStatus(RequestStatus.FAILURE);
        String fileExtension = Files.getFileExtension(attachmentContent.getFilename());
        Parser parser;

        if (fileExtension.equalsIgnoreCase(XML_FILE_EXTENSION)) {
            parser = new XmlParser();
        } else if (fileExtension.equalsIgnoreCase(JSON_FILE_EXTENSION)) {
            parser = new JsonParser();
        } else {
            requestSummary.setMessage(String.format("Invalid file format <%s>. Only XML & JSON CycloneDX SBOM are supported!", fileExtension));
            return requestSummary;
        }

        try {
            // parsing the input stream into CycloneDx org.cyclonedx.model.Bom
            Bom bom = parser.parse(IOUtils.toByteArray(inputStream));
            Metadata bomMetadata = bom.getMetadata();

            // Getting List of org.cyclonedx.model.Component from the Bom
            Predicate<ExternalReference.Type> typeFilter = type -> ExternalReference.Type.VCS.equals(type);
            List<org.cyclonedx.model.Component> components = CommonUtils.nullToEmptyList(bom.getComponents());
            long vcsCount = components.stream().map(org.cyclonedx.model.Component::getExternalReferences)
                    .filter(Objects::nonNull).flatMap(List::stream).map(ExternalReference::getType).filter(typeFilter).count();
            long componentsCount = components.size();
            org.cyclonedx.model.Component compMetadata = bomMetadata.getComponent();
            Map<String, List<org.cyclonedx.model.Component>> vcsToComponentMap = getVcsToomponentMap(components);

            if (componentsCount == vcsCount) {
                return importProjectWithComponentReleasesAndPackages(compMetadata, vcsToComponentMap, projectId, attachmentContent);
            } else if (componentsCount > vcsCount) {
                requestSummary = importProjectWithComponentReleasesAndPackages(compMetadata, vcsToComponentMap, projectId, attachmentContent);
                if (requestSummary.requestStatus.equals(RequestStatus.SUCCESS)) {
                    final Set<String> packageNameSet = new HashSet<>();
                    final Set<String> componentNameSet = new HashSet<>();
                    final Set<String> invalidPakageSet = new HashSet<>();
                    final var countMap = new HashMap<String, Integer>();
                    String jsonMessage = requestSummary.getMessage();
                    messageMap = new Gson().fromJson(jsonMessage, Map.class);
                    String projId = messageMap.get("projectId");
                    if (CommonUtils.isNullEmptyOrWhitespace(projId)) {
                        return requestSummary;
                    }
                    String packages = messageMap.get("pkg");
                    if (CommonUtils.isNotNullEmptyOrWhitespace(packages)) {
                        packageNameSet.addAll(Arrays.asList(packages.split(JOINER)));
                    }
                    Integer reuseCount = Integer.valueOf(messageMap.get(PKG_CREATION_COUNT_KEY));
                    Integer creationCOunt = Integer.valueOf(messageMap.get(PKG_REUSE_COUNT_KEY));
                    Project project = projectDatabaseHandler.getProjectById(projId, user);
                    countMap.put(PKG_CREATION_COUNT_KEY, reuseCount);
                    countMap.put(PKG_REUSE_COUNT_KEY, creationCOunt);
                    for (org.cyclonedx.model.Component comp : components) {
                        if (CommonUtils.isNullOrEmptyCollection(comp.getExternalReferences())
                                || comp.getExternalReferences().stream().map(ExternalReference::getType).filter(typeFilter).count() == 0) {
                            String fullName = "";
                            final var licenses = getLicenseFromBomComponent(comp);
                            Package pkg = createPackage(comp, null, licenses);
                            fullName = SW360Utils.getVersionedName(comp.getName(), comp.getVersion());
                            if (pkg == null || CommonUtils.isNullEmptyOrWhitespace(pkg.getName()) || CommonUtils.isNullEmptyOrWhitespace(pkg.getVersion())
                                    || CommonUtils.isNullEmptyOrWhitespace(pkg.getPurl())) {
                                invalidPakageSet.add(fullName);
                                log.error("Invalid package found in SBoM: " + fullName);
                                continue;
                            }
                            try {
                                AddDocumentRequestSummary pkgAddSummary = packageDatabaseHandler.addPackage(pkg, user);
                                componentNameSet.add(fullName);

                                if (CommonUtils.isNotNullEmptyOrWhitespace(pkgAddSummary.getId())) {
                                    project.addToPackageIds(pkgAddSummary.getId());
                                    pkg.setId(pkgAddSummary.getId());
                                    if (AddDocumentRequestStatus.DUPLICATE.equals(pkgAddSummary.getRequestStatus())) {
                                        countMap.put(PKG_REUSE_COUNT_KEY, countMap.get(PKG_REUSE_COUNT_KEY) + 1);
                                        log.info("reusing existing package: " + pkg.getId());
                                    } else {
                                        countMap.put(PKG_CREATION_COUNT_KEY, countMap.get(PKG_CREATION_COUNT_KEY) + 1);
                                        log.info("package creation successfull: " + pkg.getId());
                                    }
                                } else {
                                    // in case of more than 1 duplicate found, then return and show error message in UI.
                                    log.warn("found multiple packages: " + fullName);
                                    packageNameSet.add(fullName);
                                    componentNameSet.remove(fullName);
                                    continue;
                                }
                            } catch (SW360Exception e) {
                                log.error("Exception occured while parsing CycloneDX SBoM: ", e);
                                continue;
                            }

                        }
                    }
                    RequestStatus updateStatus = projectDatabaseHandler.updateProject(project, user);
                    if (RequestStatus.SUCCESS.equals(updateStatus)) {
                        log.info("updating project successfull: " + project.getName());
                    }
                    // all components does not have VCS, so return & show appropriate error in UI
                    messageMap.put("invalidComp", String.join(JOINER, componentNameSet));
                    messageMap.put("invalidPkgs", String.join(JOINER, invalidPakageSet));
                    messageMap.put("pkg", String.join(JOINER, packageNameSet));
                    messageMap.put("message",
                            String.format("VCS information is missing for <b>%s</b> / <b>%s</b> Components!",
                                    componentsCount - vcsCount, componentsCount));
                    messageMap.put(PKG_CREATION_COUNT_KEY, countMap.get(PKG_CREATION_COUNT_KEY).toString());
                    messageMap.put(PKG_REUSE_COUNT_KEY, countMap.get(PKG_REUSE_COUNT_KEY).toString());
                    requestSummary.setMessage(convertCollectionToJSONString(messageMap));
                    return requestSummary;
                }
                requestSummary.setMessage(convertCollectionToJSONString(messageMap));
                return requestSummary;
            } else {
                // this case is not possible, so return & show appropriate error in UI
                messageMap.put("message", String.format(String.format(
                        "SBOM import aborted with error: Multiple vcs information found in compnents, vcs found: %s and total components: %s",
                        vcsCount, componentsCount)));
                requestSummary.setMessage(convertCollectionToJSONString(messageMap));
                return requestSummary;
            }
        } catch (IOException e) {
            log.error("IOException occured while parsing CycloneDX SBoM: ", e);
            requestSummary.setMessage("IOException occured while parsing CycloneDX SBoM: " + e.getMessage());
        } catch (ParseException e) {
            log.error("ParseException occured while parsing CycloneDX SBoM: ", e);
            requestSummary.setMessage("ParseException occured while parsing CycloneDX SBoM: " + e.getMessage());
        } catch (SW360Exception e) {
            log.error("SW360Exception occured while parsing CycloneDX SBoM: ", e);
            requestSummary.setMessage("SW360Exception occured while parsing CycloneDX SBoM: " + e.getMessage());
        } catch (Exception e) {
            log.error("Exception occured while parsing CycloneDX SBoM: ", e);
            requestSummary.setMessage("An Exception occured while parsing CycloneDX SBoM: " + e.getMessage());
        }
        return requestSummary;
    }

    public RequestSummary importProjectWithComponentReleasesAndPackages(org.cyclonedx.model.Component compMetadata,
            Map<String, List<org.cyclonedx.model.Component>> vcsToComponentMap, String projectId, AttachmentContent attachmentContent)
                    throws SW360Exception {
        final RequestSummary summary = new RequestSummary();
        final Set<String> componentNames = new HashSet<>();
        final Set<String> releaseNames = new HashSet<>();
        final Set<String> packageNames = new HashSet<>();

        summary.setRequestStatus(RequestStatus.FAILURE);

        Project project;
        AddDocumentRequestSummary projectAddSummary = new AddDocumentRequestSummary();
        AddDocumentRequestStatus addStatus = projectAddSummary.getRequestStatus();

        try {
            if (CommonUtils.isNotNullEmptyOrWhitespace(projectId)) {
                project = projectDatabaseHandler.getProjectById(projectId, user);
                log.info("reusing existing project: " + projectId);
            } else {
                // Metadata component is used to created the project
                project = createProject(compMetadata);
                projectAddSummary = projectDatabaseHandler.addProject(project, user);
                addStatus = projectAddSummary.getRequestStatus();

                if (CommonUtils.isNotNullEmptyOrWhitespace(projectAddSummary.getId())) {
                    if (AddDocumentRequestStatus.SUCCESS.equals(addStatus)) {
                        project = projectDatabaseHandler.getProjectById(projectAddSummary.getId(), user);
                        log.info("project created successfully: " + projectAddSummary.getId());
                    } else if (AddDocumentRequestStatus.DUPLICATE.equals(addStatus)) {
                        log.error("cannot import SBOM for an existing project from Project List / Home page - " + projectAddSummary.getId());
                        summary.setRequestStatus(getRequestStatusFromAddDocRequestStatus(addStatus));
                        summary.setMessage(String.format("A project with same name and version already exist with id: <b>%s</b> <br> Please import this SBOM from project details page!", projectAddSummary.getId()));
                        return summary;
                    }
                } else {
                    summary.setRequestStatus(getRequestStatusFromAddDocRequestStatus(addStatus));
                    summary.setMessage("Invalid Projct metadata present in SBOM or Multiple project with same name and version is already present in SW360!");
                    return summary;
                }

            }
        } catch (SW360Exception e) {
            log.error("An error occured while importing project from SBOM: " + e.getMessage());
            summary.setMessage("An error occured while importing project from SBOM!");
            return summary;
        }

        final var countMap = new HashMap<String, Integer>();
        countMap.put(COMP_CREATION_COUNT_KEY, 0);
        countMap.put(COMP_REUSE_COUNT_KEY, 0);
        countMap.put(REL_CREATION_COUNT_KEY, 0);
        countMap.put(REL_REUSE_COUNT_KEY, 0);
        countMap.put(PKG_CREATION_COUNT_KEY, 0);
        countMap.put(PKG_REUSE_COUNT_KEY, 0);

        for (Map.Entry<String, List<org.cyclonedx.model.Component>> entry : vcsToComponentMap.entrySet()) {
            Component comp = createComponent(entry.getKey());
            Release rel = new Release();
            String relName = "";
            AddDocumentRequestSummary compAddSummary;
            try {
                compAddSummary = componentDatabaseHandler.addComponent(comp, user.getEmail());

                if (CommonUtils.isNotNullEmptyOrWhitespace(compAddSummary.getId())) {
                    comp.setId(compAddSummary.getId());
                    if (AddDocumentRequestStatus.SUCCESS.equals(compAddSummary.getRequestStatus())) {
                        countMap.put(COMP_CREATION_COUNT_KEY, countMap.get(COMP_CREATION_COUNT_KEY) + 1);
                        log.info("component creation successfull: " + comp.getId());
                    } else {
                        countMap.put(COMP_REUSE_COUNT_KEY, countMap.get(COMP_REUSE_COUNT_KEY) + 1);
                        log.info("reusing existing component: " + comp.getId());
                    }
                } else {
                    // in case of more than 1 duplicate found, then continue and show error message in UI.
                    log.warn("found multiple components: " + comp.getName());
                    componentNames.add(comp.getName());
                    continue;
                }

                for (org.cyclonedx.model.Component bomComp : entry.getValue()) {
                    Set<String> licenses = getLicenseFromBomComponent(bomComp);
                    rel = createRelease(bomComp, comp, licenses);
                    relName = SW360Utils.getVersionedName(rel.getName(), rel.getVersion());

                    try {
                        AddDocumentRequestSummary relAddSummary = componentDatabaseHandler.addRelease(rel, user);
                        if (CommonUtils.isNotNullEmptyOrWhitespace(relAddSummary.getId())) {
                            rel.setId(relAddSummary.getId());
                            if (AddDocumentRequestStatus.SUCCESS.equals(relAddSummary.getRequestStatus())) {
                                countMap.put(REL_CREATION_COUNT_KEY, countMap.get(REL_CREATION_COUNT_KEY) + 1);
                                log.info("release creation successfull: " + rel.getId());
                            } else {
                                countMap.put(REL_REUSE_COUNT_KEY, countMap.get(REL_REUSE_COUNT_KEY) + 1);
                                log.info("reusing existing release: " + rel.getId());
                            }
                        } else {
                            // in case of more than 1 duplicate found, then continue and show error message in UI.
                            log.warn("found multiple releases: " + relName);
                            releaseNames.add(relName);
                            continue;
                        }
                    } catch (SW360Exception e) {
                        log.error("An error occured while creating/adding release from SBOM: " + e.getMessage());
                        summary.setMessage("An error occured while creating/adding release from SBOM!");
                        return summary;
                    }

                    Package pkg = createPackage(bomComp, rel, licenses);
                    String pkgName = SW360Utils.getVersionedName(pkg.getName(), pkg.getVersion());

                    try {
                        AddDocumentRequestSummary pkgAddSummary = packageDatabaseHandler.addPackage(pkg, user);
                        // update components specific fields 
                        comp = componentDatabaseHandler.getComponent(compAddSummary.getId(), user);
                        if (null != bomComp.getType()) {
                            Set<String> categories = new HashSet<>(comp.getCategories());
                            categories.add(bomComp.getType().getTypeName());
                            categories.remove(DEFAULT_CATEGORY);
                            comp.setCategories(categories);
                        }
                        if (CommonUtils.isNullEmptyOrWhitespace(comp.getDescription())) {
                            comp.setDescription(CommonUtils.nullToEmptyString(bomComp.getDescription()).trim());
                        }
                        if (comp.isSetMainLicenseIds()) {
                            comp.getMainLicenseIds().addAll(licenses);
                        } else {
                            comp.setMainLicenseIds(licenses);
                        }

                        if (CommonUtils.isNotNullEmptyOrWhitespace(pkgAddSummary.getId())) {
                            pkg.setId(pkgAddSummary.getId());
                            if (AddDocumentRequestStatus.DUPLICATE.equals(pkgAddSummary.getRequestStatus())) {
                                Package dupPkg = packageDatabaseHandler.getPackageById(pkg.getId());
                                if (!rel.getId().equals(dupPkg.getReleaseId())) {
                                    log.error("Release Id of Package from BOM: '%s' and Database: '%s' is not equal!", rel.getId(), dupPkg.getReleaseId());
                                    dupPkg.setReleaseId(rel.getId());
                                    packageDatabaseHandler.updatePackage(dupPkg, user, true);
                                }
                                countMap.put(PKG_REUSE_COUNT_KEY, countMap.get(PKG_REUSE_COUNT_KEY) + 1);
                                log.info("reusing existing package: " + pkg.getId());
                            } else {
                                countMap.put(PKG_CREATION_COUNT_KEY, countMap.get(PKG_CREATION_COUNT_KEY) + 1);
                                log.info("package creation successfull: " + pkg.getId());
                            }
                        } else {
                            // in case of more than 1 duplicate found, then continue and show error message in UI.
                            log.warn("found multiple packages: " + pkgName);
                            packageNames.add(pkgName);
                            continue;
                        }
                        project.addToPackageIds(pkg.getId());
                        project.putToReleaseIdToUsage(rel.getId(), getDefaultRelation());
                    } catch (SW360Exception e) {
                        log.error("An error occured while creating/adding package from SBOM: " + e.getMessage());
                        summary.setMessage("An error occured while creating/adding package from SBOM!");
                        return summary;
                    }
                }

                RequestStatus updateStatus = componentDatabaseHandler.updateComponent(comp, user, true);
                if (RequestStatus.SUCCESS.equals(updateStatus)) {
                    log.info("updating component successfull: " + comp.getName());
                }
            } catch (SW360Exception e) {
                log.error("An error occured while importing CycloneDX SBOM: " + e.getMessage());
                summary.setMessage("An error occured while importing CycloneDX SBOM!");
                return summary;
            }
        }

        try {
            if (attachmentContent != null) {
                Attachment attachment = makeAttachmentFromContent(attachmentContent);
                project.setAttachments(Collections.singleton(attachment));
            }
            RequestStatus updateStatus = projectDatabaseHandler.updateProject(project, user, true);
            if (RequestStatus.SUCCESS.equals(updateStatus)) {
                log.info("updating project successfull: " + project.getName());
            }
        } catch (SW360Exception e) {
            log.error("An error occured while updating project from SBOM: " + e.getMessage());
            summary.setMessage(
                    "An error occured while updating project during SBOM import, please delete the project and re-import SBOM!");
            return summary;
        }

       final Map<String, String> messageMap = new HashMap<>();
        messageMap.put("comp", String.join(JOINER, componentNames));
        messageMap.put("rel", String.join(JOINER, releaseNames));
        messageMap.put("pkg", String.join(JOINER, packageNames));
        messageMap.put("projectId", project.getId());
        messageMap.put("projectName", SW360Utils.getVersionedName(project.getName(), project.getVersion()));
        messageMap.put(COMP_CREATION_COUNT_KEY, countMap.get(COMP_CREATION_COUNT_KEY).toString());
        messageMap.put(COMP_REUSE_COUNT_KEY, countMap.get(COMP_REUSE_COUNT_KEY).toString());
        messageMap.put(REL_CREATION_COUNT_KEY, countMap.get(REL_CREATION_COUNT_KEY).toString());
        messageMap.put(REL_REUSE_COUNT_KEY, countMap.get(REL_REUSE_COUNT_KEY).toString());
        messageMap.put(PKG_CREATION_COUNT_KEY, countMap.get(PKG_CREATION_COUNT_KEY).toString());
        messageMap.put(PKG_REUSE_COUNT_KEY, countMap.get(PKG_REUSE_COUNT_KEY).toString());

        summary.setMessage(convertCollectionToJSONString(messageMap));
        summary.setRequestStatus(RequestStatus.SUCCESS);
        return summary;
    }

    private Set<String> getLicenseFromBomComponent(org.cyclonedx.model.Component comp) {
        Set<String> licenses = new HashSet<>();
        if (Objects.nonNull(comp.getLicenseChoice()) && CommonUtils.isNotEmpty(comp.getLicenseChoice().getLicenses())) {
            licenses.addAll(comp.getLicenseChoice().getLicenses().stream()
                    .map(lic -> (null == lic.getId()) ? lic.getName() : lic.getId()).collect(Collectors.toSet()));
        }
        return licenses;
    }

    private String convertCollectionToJSONString(Map<String, String> map) throws SW360Exception {
        try {
            JsonFactory factory = new JsonFactory();
            StringWriter jsonObjectWriter = new StringWriter();
            JsonGenerator jsonGenerator = factory.createGenerator(jsonObjectWriter);
            jsonGenerator.useDefaultPrettyPrinter();
            jsonGenerator.writeStartObject();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                jsonGenerator.writeStringField(entry.getKey(), entry.getValue());
            }
            jsonGenerator.writeEndObject();
            jsonGenerator.close();
            return jsonObjectWriter.toString();
        } catch (IOException e) {
            throw new SW360Exception("An exception occured while generating JSON info for BOM import! " + e.getMessage());
        }
    }

    private RequestStatus getRequestStatusFromAddDocRequestStatus(AddDocumentRequestStatus status) {
        switch (status) {
            case SUCCESS:
                return RequestStatus.SUCCESS;
            case DUPLICATE:
                return RequestStatus.DUPLICATE;
            case NAMINGERROR:
                return RequestStatus.NAMINGERROR;
            case INVALID_INPUT:
                return RequestStatus.INVALID_INPUT;
            default:
                return RequestStatus.FAILURE;
        }
    }

    private Attachment makeAttachmentFromContent(AttachmentContent attachmentContent) {
        Attachment attachment = new Attachment();
        attachment.setAttachmentContentId(attachmentContent.getId());
        attachment.setAttachmentType(AttachmentType.SBOM);
        attachment.setCreatedComment("Auto generated - Used for importing CycloneDX SBOM");
        attachment.setFilename(attachmentContent.getFilename());
        attachment.setCreatedOn(SW360Utils.getCreatedOn());
        attachment.setCreatedBy(user.getEmail());
        attachment.setCheckStatus(CheckStatus.NOTCHECKED);

        return attachment;
    }

    private Project createProject(org.cyclonedx.model.Component compMetadata) {
        return new Project(CommonUtils.nullToEmptyString(compMetadata.getName()).trim())
                .setVersion(CommonUtils.nullToEmptyString(compMetadata.getVersion()).trim())
                .setDescription(CommonUtils.nullToEmptyString(compMetadata.getDescription()).trim()).setType(ThriftEnumUtils.enumToString(ProjectType.PRODUCT))
                .setBusinessUnit(user.getDepartment()).setVisbility(Visibility.EVERYONE);
    }

    private Component createComponent(String name) {
        Component component = new Component();
        component.setName(name);
        component.setComponentType(ComponentType.OSS);
        return component;
    }

    private Release createRelease(org.cyclonedx.model.Component componentFromBom, Component component,
            Set<String> licenses) {
        Release release = new Release();
        release.setName(component.getName());
        release.setVersion(StringUtils.defaultIfBlank(componentFromBom.getVersion(), "NA"));
        release.setComponentId(component.getId());
        release.setCreatorDepartment(user.getDepartment());
        if (release.isSetMainLicenseIds()) {
            release.getMainLicenseIds().addAll(licenses);
        } else {
            release.setMainLicenseIds(licenses);
        }
        for (ExternalReference ref : componentFromBom.getExternalReferences()) {
            if (ExternalReference.Type.VCS.equals(ref.getType())) {
                release.setSourceCodeDownloadurl(ref.getUrl().replaceAll(VCS_HTTP_REGEX, HTTPS_SCHEME));
            }
        }
        return release;
    }

    private static ProjectReleaseRelationship getDefaultRelation() {
        return new ProjectReleaseRelationship(ReleaseRelationship.UNKNOWN, MainlineState.OPEN);
    }

    private String getPackageName(PackageURL packageURL, org.cyclonedx.model.Component comp, String delimiter) {
        String name = CommonUtils.nullToEmptyString(packageURL.getName());
        if (CommonUtils.isNotNullEmptyOrWhitespace(packageURL.getNamespace())) {
            return new StringBuilder(packageURL.getNamespace()).append(delimiter).append(name).toString();
        } else if (CommonUtils.isNotNullEmptyOrWhitespace(comp.getGroup())) {
            return new StringBuilder(comp.getGroup()).append(delimiter).append(name).toString();
        } else if (CommonUtils.isNotNullEmptyOrWhitespace(comp.getPublisher())) { //.replaceAll(CLEAN_PUBLISHER_REGEX, StringUtils.EMPTY)
            return new StringBuilder(comp.getPublisher()).append(delimiter).append(name).toString();
        } else {
            return name;
        }
    }

    private Package createPackage(org.cyclonedx.model.Component componentFromBom, Release release, Set<String> licenses) {
        Package pckg = new Package();
        String purl = componentFromBom.getPurl();
        if (CommonUtils.isNotNullEmptyOrWhitespace(purl)) {
            try {
                purl = purl.toLowerCase().trim();
                PackageURL packageURL = new PackageURL(purl);
                pckg.setPackageManagerType(PackageManagerType.valueOf(packageURL.getType().toUpperCase()));
                pckg.setPurl(purl);
                String packageName = StringUtils.EMPTY;
                if (PackageManagerType.NPM.toString().equalsIgnoreCase(packageURL.getType())
                        || PackageManagerType.GOLANG.toString().equalsIgnoreCase(packageURL.getType())) {
                    packageName = getPackageName(packageURL, componentFromBom, SLASH).trim();
                } else {
                    packageName = getPackageName(packageURL, componentFromBom, DOT).replaceAll(SLASH, DOT).trim();
                }
                pckg.setName(packageName);
                pckg.setVersion(packageURL.getVersion());
            } catch (MalformedPackageURLException e) {
                log.error("Malformed PURL for component: " + componentFromBom.getName(), e);
                return null;
            }
        } else {
            return null;
        }

        if (release != null && release.isSetId()) {
            pckg.setReleaseId(release.getId());
        }
        pckg.setDescription(CommonUtils.nullToEmptyString(componentFromBom.getDescription()).trim());

        // String vendorName = key.replaceAll(VENDOR_REGEX, "");
        // if (CommonUtils.isNotNullEmptyOrWhitespace(vendorName)) {
        // VendorService.Iface vendorClient = new ThriftClients().makeVendorClient();
        // try {
        // Vendor vendor = new Vendor(vendorName, vendorName.toUpperCase(), "");
        // vendorClient.addVendor(null);
        // } catch (TException e) {
        // e.printStackTrace();
        // }
        // vendorRepository.getVendorByLowercaseShortnamePrefix(vendorName);
        // }

        for (ExternalReference ref : CommonUtils.nullToEmptyList(componentFromBom.getExternalReferences())) {
            if (ExternalReference.Type.WEBSITE.equals(ref.getType())) {
                pckg.setHomepageUrl(ref.getUrl());
            }
            if (ExternalReference.Type.VCS.equals(ref.getType())) {
                pckg.setVcs(ref.getUrl().replaceAll(VCS_HTTP_REGEX, HTTPS_SCHEME));
            }
        }

        // if (CommonUtils.isNotNullEmptyOrWhitespace(purl)) {
        // Matcher matcher = PURL_PATTERN.matcher(purl);
        // if (matcher.find()) {
        // pckg.setPackageManagerType(PackageManagerType.valueOf(matcher.group().toUpperCase()));
        // }
        // }

        if (pckg.isSetLicenseIds()) {
            pckg.getLicenseIds().addAll(licenses);
        } else {
            pckg.setLicenseIds(licenses);
        }
        return pckg;
    }
}

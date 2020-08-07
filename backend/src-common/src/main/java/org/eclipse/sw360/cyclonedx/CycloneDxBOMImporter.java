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
import org.eclipse.sw360.common.utils.BackendUtils;
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
    private static final String DUPLICATE_COMPONENT = "dupComp";
    private static final String DUPLICATE_RELEASE = "dupRel";
    private static final String DUPLICATE_PACKAGE = "dupPkg";
    private static final String INVALID_COMPONENT = "invalidComp";
    private static final String INVALID_RELEASE = "invalidRel";
    private static final String INVALID_PACKAGE = "invalidPkg";
    private static final String DEFAULT_CATEGORY = "Default_Category";
    private static final String PROJECT_ID = "projectId";
    private static final String PROJECT_NAME = "projectName";
    private static final boolean IS_PACKAGE_PORTLET_DISABLED = BackendUtils.IS_PACKAGE_PORTLET_DISABLED;
    private static final Predicate<ExternalReference.Type> typeFilter = type -> ExternalReference.Type.VCS.equals(type);

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
    private Map<String, List<org.cyclonedx.model.Component>> getVcsToComponentMap(List<org.cyclonedx.model.Component> components) {
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
            List<org.cyclonedx.model.Component> components = CommonUtils.nullToEmptyList(bom.getComponents());

            long vcsCount = components.stream().map(org.cyclonedx.model.Component::getExternalReferences)
                    .filter(Objects::nonNull).flatMap(List::stream).map(ExternalReference::getType).filter(typeFilter).count();
            long componentsCount = components.size();
            org.cyclonedx.model.Component compMetadata = bomMetadata.getComponent();
            Map<String, List<org.cyclonedx.model.Component>> vcsToComponentMap = new HashMap<>();

            if (IS_PACKAGE_PORTLET_DISABLED) {
                vcsToComponentMap.put("", components);
                return importSbomAsProject(compMetadata, vcsToComponentMap, projectId, attachmentContent);
            }

            vcsToComponentMap = getVcsToComponentMap(components);
            if (componentsCount == vcsCount) {

                return importSbomAsProject(compMetadata, vcsToComponentMap, projectId, attachmentContent);
            } else if (componentsCount > vcsCount) {

                requestSummary = importSbomAsProject(compMetadata, vcsToComponentMap, projectId, attachmentContent);

                if (requestSummary.requestStatus.equals(RequestStatus.SUCCESS)) {

                    String jsonMessage = requestSummary.getMessage();
                    messageMap = new Gson().fromJson(jsonMessage, Map.class);
                    String projId = messageMap.get("projectId");

                    if (CommonUtils.isNullEmptyOrWhitespace(projId)) {
                        return requestSummary;
                    }
                    final Set<String> duplicatePackages = new HashSet<>();
                    final Set<String> componentsWithoutVcs = new HashSet<>();
                    final Set<String> invalidPackages = new HashSet<>();

                    Integer reuseCount = Integer.valueOf(messageMap.get(PKG_REUSE_COUNT_KEY));
                    Integer creationCount = Integer.valueOf(messageMap.get(PKG_CREATION_COUNT_KEY));

                    String packages = messageMap.get(DUPLICATE_PACKAGE);
                    if (CommonUtils.isNotNullEmptyOrWhitespace(packages)) {
                        duplicatePackages.addAll(Arrays.asList(packages.split(JOINER)));
                        packages = "";
                    }
                    packages = messageMap.get(INVALID_PACKAGE);
                    if (CommonUtils.isNotNullEmptyOrWhitespace(packages)) {
                        invalidPackages.addAll(Arrays.asList(packages.split(JOINER)));
                        packages = "";
                    }
                    Project project = projectDatabaseHandler.getProjectById(projId, user);

                    for (org.cyclonedx.model.Component comp : components) {
                        if (CommonUtils.isNullOrEmptyCollection(comp.getExternalReferences())
                                || comp.getExternalReferences().stream().map(ExternalReference::getType).filter(typeFilter).count() == 0) {

                            final var fullName = SW360Utils.getVersionedName(comp.getName(), comp.getVersion());
                            final var licenses = getLicenseFromBomComponent(comp);
                            final Package pkg = createPackage(comp, null, licenses);

                            if (pkg == null || CommonUtils.isNullEmptyOrWhitespace(pkg.getName()) || CommonUtils.isNullEmptyOrWhitespace(pkg.getVersion())
                                    || CommonUtils.isNullEmptyOrWhitespace(pkg.getPurl())) {
                                invalidPackages.add(fullName);
                                log.error(String.format("Invalid package '%s' found in SBoM, missing name or version or purl! ", fullName));
                                continue;
                            }

                            try {
                                AddDocumentRequestSummary pkgAddSummary = packageDatabaseHandler.addPackage(pkg, user);
                                componentsWithoutVcs.add(fullName);

                                if (CommonUtils.isNotNullEmptyOrWhitespace(pkgAddSummary.getId())) {
                                    project.addToPackageIds(pkgAddSummary.getId());
                                    pkg.setId(pkgAddSummary.getId());
                                    if (AddDocumentRequestStatus.DUPLICATE.equals(pkgAddSummary.getRequestStatus())) {
                                        reuseCount++;
                                    } else {
                                        creationCount++;
                                    }
                                } else {
                                    // in case of more than 1 duplicate found, then return and show error message in UI.
                                    log.warn("found multiple packages: " + fullName);
                                    duplicatePackages.add(fullName);
                                    continue;
                                }
                            } catch (SW360Exception e) {
                                log.error("An error occured while creating/adding package from SBOM: " + e.getMessage());
                                continue;
                            }

                        }
                    }
                    RequestStatus updateStatus = projectDatabaseHandler.updateProject(project, user);
                    if (RequestStatus.SUCCESS.equals(updateStatus)) {
                        log.info("linking packages to project successfull: " + projId);
                    }
                    // all components does not have VCS, so return & show appropriate error in UI
                    messageMap.put(INVALID_COMPONENT, String.join(JOINER, componentsWithoutVcs));
                    messageMap.put(INVALID_PACKAGE, String.join(JOINER, invalidPackages));
                    messageMap.put(DUPLICATE_PACKAGE, String.join(JOINER, duplicatePackages));
                    messageMap.put("message",
                            String.format("VCS information is missing for <b>%s</b> / <b>%s</b> Components!",
                                    componentsCount - vcsCount, componentsCount));
                    messageMap.put(PKG_CREATION_COUNT_KEY, String.valueOf(creationCount));
                    messageMap.put(PKG_REUSE_COUNT_KEY, String.valueOf(reuseCount));
                    requestSummary.setMessage(convertCollectionToJSONString(messageMap));
                    return requestSummary;
                }
                requestSummary.setMessage(convertCollectionToJSONString(messageMap));
                return requestSummary;
            } else {
                // this case is not possible, so return & show appropriate error in UI
                requestSummary.setMessage(String.format(String.format(
                        "SBOM import aborted with error: Multiple vcs information found in compnents, vcs found: %s and total components: %s",
                        vcsCount, componentsCount)));
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

    public RequestSummary importSbomAsProject(org.cyclonedx.model.Component compMetadata,
            Map<String, List<org.cyclonedx.model.Component>> vcsToComponentMap, String projectId, AttachmentContent attachmentContent)
                    throws SW360Exception {
        final RequestSummary summary = new RequestSummary();
        summary.setRequestStatus(RequestStatus.FAILURE);

        Project project;
        AddDocumentRequestSummary projectAddSummary = new AddDocumentRequestSummary();
        AddDocumentRequestStatus addStatus = projectAddSummary.getRequestStatus();
        Map<String, String> messageMap = new HashMap<>();

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
                        messageMap.put(PROJECT_ID, projectAddSummary.getId());
                        messageMap.put(PROJECT_NAME, SW360Utils.getVersionedName(project.getName(), project.getVersion()));
                        messageMap.put("message", "A project with same name and version already exists in SW360, Please import this SBOM from project details page!");
                        summary.setMessage(convertCollectionToJSONString(messageMap));
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

        if (IS_PACKAGE_PORTLET_DISABLED) {
            messageMap = importAllComponentsAsReleases(vcsToComponentMap, project);
        } else {
            messageMap = importAllComponentsAsPackages(vcsToComponentMap, project);
        }
        try {
            if (attachmentContent != null) {
                Attachment attachment = makeAttachmentFromContent(attachmentContent, project.getAttachmentsSize());
                project.addToAttachments(attachment);
            }
            RequestStatus updateStatus = projectDatabaseHandler.updateProject(project, user, true);
            if (RequestStatus.SUCCESS.equals(updateStatus)) {
                log.info("updating project successfull: " + project.getName());
            }
        } catch (SW360Exception e) {
            log.error("An error occured while updating project from SBOM: " + e.getMessage());
            summary.setMessage("An error occured while updating project during SBOM import, please delete the project and re-import SBOM!");
            return summary;
        }

        summary.setMessage(convertCollectionToJSONString(messageMap));
        summary.setRequestStatus(RequestStatus.SUCCESS);
        return summary;
    }

    private Map<String, String> importAllComponentsAsReleases(Map<String, List<org.cyclonedx.model.Component>> vcsToComponentMap, Project project) {

        final var countMap = new HashMap<String, Integer>();
        final Set<String> duplicateComponents = new HashSet<>();
        final Set<String> duplicateReleases = new HashSet<>();
        final Set<String> invalidReleases = new HashSet<>();
        countMap.put(COMP_CREATION_COUNT_KEY, 0); countMap.put(COMP_REUSE_COUNT_KEY, 0);
        countMap.put(REL_CREATION_COUNT_KEY, 0); countMap.put(REL_REUSE_COUNT_KEY, 0);
        int compCreationCount = 0, compReuseCount = 0, relCreationCount = 0, relReuseCount = 0;

        final List<org.cyclonedx.model.Component> components = vcsToComponentMap.get("");
        for (org.cyclonedx.model.Component bomComp : components) {
            Component comp = createComponent(bomComp);
            if (CommonUtils.isNullEmptyOrWhitespace(comp.getName()) ) {
                log.error("component name is not present in SBoM: " + project.getId());
                continue;
            }
            String relName = "";
            AddDocumentRequestSummary compAddSummary;
            try {
                compAddSummary = componentDatabaseHandler.addComponent(comp, user.getEmail());

                if (CommonUtils.isNotNullEmptyOrWhitespace(compAddSummary.getId())) {
                    comp.setId(compAddSummary.getId());
                    if (AddDocumentRequestStatus.SUCCESS.equals(compAddSummary.getRequestStatus())) {
                        compCreationCount++;
                    } else {
                        compReuseCount++;
                    }
                } else {
                    // in case of more than 1 duplicate found, then continue and show error message in UI.
                    log.warn("found multiple components: " + comp.getName());
                    duplicateComponents.add(comp.getName());
                    continue;
                }

                Release rel = new Release();
                Set<String> licenses = getLicenseFromBomComponent(bomComp);
                rel = createRelease(bomComp, comp, licenses);
                if (CommonUtils.isNullEmptyOrWhitespace(rel.getVersion()) ) {
                    log.error("release version is not present in SBoM for component: " + comp.getName());
                    invalidReleases.add(comp.getName());
                    continue;
                }
                relName = SW360Utils.getVersionedName(rel.getName(), rel.getVersion());

                try {
                    AddDocumentRequestSummary relAddSummary = componentDatabaseHandler.addRelease(rel, user);
                    if (CommonUtils.isNotNullEmptyOrWhitespace(relAddSummary.getId())) {
                        rel.setId(relAddSummary.getId());
                        if (AddDocumentRequestStatus.SUCCESS.equals(relAddSummary.getRequestStatus())) {
                            relCreationCount++;
                        } else {
                            relReuseCount++;
                        }
                    } else {
                        // in case of more than 1 duplicate found, then continue and show error message in UI.
                        log.warn("found multiple releases: " + relName);
                        duplicateReleases.add(relName);
                        continue;
                    }
                    project.putToReleaseIdToUsage(rel.getId(), getDefaultRelation());
                } catch (SW360Exception e) {
                    log.error("An error occured while creating/adding release from SBOM: " + e.getMessage());
                    continue;
                }

                // update components specific fields
                comp = componentDatabaseHandler.getComponent(compAddSummary.getId(), user);
                if (null != bomComp.getType()) {
                    Set<String> categories = new HashSet<>(comp.getCategories());
                    categories.add(bomComp.getType().getTypeName());
                    categories.remove(DEFAULT_CATEGORY);
                    comp.setCategories(categories);
                }
                StringBuilder description = new StringBuilder();
                if (CommonUtils.isNullEmptyOrWhitespace(comp.getDescription())) {
                    description.append(CommonUtils.nullToEmptyString(bomComp.getDescription()).trim());
                } else {
                    description.append(" | ").append(CommonUtils.nullToEmptyString(bomComp.getDescription()).trim());
                }
                if (comp.isSetMainLicenseIds()) {
                    comp.getMainLicenseIds().addAll(licenses);
                } else {
                    comp.setMainLicenseIds(licenses);
                }
                comp.setDescription(description.toString());
                RequestStatus updateStatus = componentDatabaseHandler.updateComponent(comp, user, true);
                if (RequestStatus.SUCCESS.equals(updateStatus)) {
                    log.info("updating component successfull: " + comp.getName());
                }
            } catch (SW360Exception e) {
                log.error("An error occured while creating/adding component from SBOM: " + e.getMessage());
                continue;
            }
        }

        final Map<String, String> messageMap = new HashMap<>();
        messageMap.put(DUPLICATE_COMPONENT, String.join(JOINER, duplicateComponents));
        messageMap.put(DUPLICATE_RELEASE, String.join(JOINER, duplicateReleases));
        messageMap.put(INVALID_RELEASE, String.join(JOINER, invalidReleases));
        messageMap.put(PROJECT_ID, project.getId());
        messageMap.put(PROJECT_NAME, SW360Utils.getVersionedName(project.getName(), project.getVersion()));
        messageMap.put(COMP_CREATION_COUNT_KEY, String.valueOf(compCreationCount));
        messageMap.put(COMP_REUSE_COUNT_KEY, String.valueOf(compReuseCount));
        messageMap.put(REL_CREATION_COUNT_KEY, String.valueOf(relCreationCount));
        messageMap.put(REL_REUSE_COUNT_KEY, String.valueOf(relReuseCount));
        return messageMap;
    }

    private Map<String, String> importAllComponentsAsPackages(Map<String, List<org.cyclonedx.model.Component>> vcsToComponentMap, Project project) {

        final var countMap = new HashMap<String, Integer>();
        final Set<String> duplicateComponents = new HashSet<>();
        final Set<String> duplicateReleases = new HashSet<>();
        final Set<String> duplicatePackages = new HashSet<>();
        final Set<String> invalidReleases = new HashSet<>();
        final Set<String> invalidPackages = new HashSet<>();
        final Map<String, ProjectReleaseRelationship> releaseRelationMap = CommonUtils.isNullOrEmptyMap(project.getReleaseIdToUsage()) ? new HashMap<>() : project.getReleaseIdToUsage();
        countMap.put(REL_CREATION_COUNT_KEY, 0); countMap.put(REL_REUSE_COUNT_KEY, 0);
        countMap.put(PKG_CREATION_COUNT_KEY, 0); countMap.put(PKG_REUSE_COUNT_KEY, 0);
        int relCreationCount = 0, relReuseCount = 0, pkgCreationCount = 0, pkgReuseCount = 0;

        for (Map.Entry<String, List<org.cyclonedx.model.Component>> entry : vcsToComponentMap.entrySet()) {
            Component comp = createComponent(entry.getKey());
            Release rel = new Release();
            String relName = "";
            StringBuilder description = new StringBuilder();
            AddDocumentRequestSummary compAddSummary;
            try {
                compAddSummary = componentDatabaseHandler.addComponent(comp, user.getEmail());

                if (CommonUtils.isNotNullEmptyOrWhitespace(compAddSummary.getId())) {
                    comp.setId(compAddSummary.getId());
                    if (AddDocumentRequestStatus.SUCCESS.equals(compAddSummary.getRequestStatus())) {
                    } else {
                    }
                } else {
                    // in case of more than 1 duplicate found, then continue and show error message in UI.
                    log.warn("found multiple components: " + comp.getName());
                    duplicateComponents.add(comp.getName());
                    continue;
                }

                for (org.cyclonedx.model.Component bomComp : entry.getValue()) {

                    Set<String> licenses = getLicenseFromBomComponent(bomComp);
                    rel = createRelease(bomComp, comp, licenses);
                    if (CommonUtils.isNullEmptyOrWhitespace(rel.getVersion()) ) {
                        log.error("release version is not present in SBoM for component: " + comp.getName());
                        invalidReleases.add(comp.getName());
                        continue;
                    }
                    relName = SW360Utils.getVersionedName(rel.getName(), rel.getVersion());

                    try {
                        AddDocumentRequestSummary relAddSummary = componentDatabaseHandler.addRelease(rel, user);
                        if (CommonUtils.isNotNullEmptyOrWhitespace(relAddSummary.getId())) {
                            rel.setId(relAddSummary.getId());
                            if (AddDocumentRequestStatus.SUCCESS.equals(relAddSummary.getRequestStatus())) {
                                relCreationCount = releaseRelationMap.containsKey(rel.getId()) ? relCreationCount : relCreationCount + 1;
                            } else {
                                relReuseCount = releaseRelationMap.containsKey(rel.getId()) ? relReuseCount : relReuseCount + 1;
                            }
                        } else {
                            // in case of more than 1 duplicate found, then continue and show error message in UI.
                            log.warn("found multiple releases: " + relName);
                            duplicateReleases.add(relName);
                            continue;
                        }
                    } catch (SW360Exception e) {
                        log.error("An error occured while creating/adding release from SBOM: " + e.getMessage());
                        continue;
                    }

                    // update components specific fields
                    comp = componentDatabaseHandler.getComponent(compAddSummary.getId(), user);
                    if (null != bomComp.getType()) {
                        Set<String> categories = new HashSet<>(comp.getCategories());
                        categories.add(bomComp.getType().getTypeName());
                        categories.remove(DEFAULT_CATEGORY);
                        comp.setCategories(categories);
                    }
                    if (CommonUtils.isNullEmptyOrWhitespace(comp.getDescription())) {
                        description.append(CommonUtils.nullToEmptyString(bomComp.getDescription()).trim());
                    } else {
                        description.append(" | ").append(CommonUtils.nullToEmptyString(bomComp.getDescription()).trim());
                    }
                    if (comp.isSetMainLicenseIds()) {
                        comp.getMainLicenseIds().addAll(licenses);
                    } else {
                        comp.setMainLicenseIds(licenses);
                    }
                    comp.setDescription(description.toString());
                    RequestStatus updateStatus = componentDatabaseHandler.updateComponent(comp, user, true);
                    if (RequestStatus.SUCCESS.equals(updateStatus)) {
                        log.info("updating component successfull: " + comp.getName());
                    }

                    Package pkg = createPackage(bomComp, rel, licenses);
                    String pkgName = SW360Utils.getVersionedName(rel.getName(), rel.getVersion());
                    if (pkg == null || CommonUtils.isNullEmptyOrWhitespace(pkg.getName()) || CommonUtils.isNullEmptyOrWhitespace(pkg.getVersion())
                            || CommonUtils.isNullEmptyOrWhitespace(pkg.getPurl())) {
                        invalidPackages.add(pkgName);
                        log.error(String.format("Invalid package '%s' found in SBoM, missing name or version or purl! ", pkgName));
                        continue;
                    }

                    try {
                        AddDocumentRequestSummary pkgAddSummary = packageDatabaseHandler.addPackage(pkg, user);
                        if (CommonUtils.isNotNullEmptyOrWhitespace(pkgAddSummary.getId())) {
                            pkg.setId(pkgAddSummary.getId());
                            if (AddDocumentRequestStatus.DUPLICATE.equals(pkgAddSummary.getRequestStatus())) {
                                Package dupPkg = packageDatabaseHandler.getPackageById(pkg.getId());
                                if (!rel.getId().equals(dupPkg.getReleaseId())) {
                                    log.error("Release Id of Package from BOM: '%s' and Database: '%s' is not equal!", rel.getId(), dupPkg.getReleaseId());
                                    dupPkg.setReleaseId(rel.getId());
                                    packageDatabaseHandler.updatePackage(dupPkg, user, true);
                                }
                                pkgReuseCount++;
                            } else {
                                pkgCreationCount++;
                            }
                        } else {
                            // in case of more than 1 duplicate found, then continue and show error message in UI.
                            log.warn("found multiple packages: " + pkgName);
                            duplicatePackages.add(pkgName);
                            continue;
                        }
                        project.addToPackageIds(pkg.getId());
                        releaseRelationMap.putIfAbsent(rel.getId(), getDefaultRelation());
                    } catch (SW360Exception e) {
                        log.error("An error occured while creating/adding package from SBOM: " + e.getMessage());
                        continue;
                    }
                }
            } catch (SW360Exception e) {
                log.error("An error occured while creating/adding component from SBOM: " + e.getMessage());
                continue;
            }
        }

        project.setReleaseIdToUsage(releaseRelationMap);
        final Map<String, String> messageMap = new HashMap<>();
        messageMap.put(DUPLICATE_COMPONENT, String.join(JOINER, duplicateComponents));
        messageMap.put(DUPLICATE_RELEASE, String.join(JOINER, duplicateReleases));
        messageMap.put(DUPLICATE_PACKAGE, String.join(JOINER, duplicatePackages));
        messageMap.put(INVALID_RELEASE, String.join(JOINER, invalidReleases));
        messageMap.put(INVALID_PACKAGE, String.join(JOINER, invalidPackages));
        messageMap.put(PROJECT_ID, project.getId());
        messageMap.put(PROJECT_NAME, SW360Utils.getVersionedName(project.getName(), project.getVersion()));
        messageMap.put(REL_CREATION_COUNT_KEY, String.valueOf(relCreationCount));
        messageMap.put(REL_REUSE_COUNT_KEY, String.valueOf(relReuseCount));
        messageMap.put(PKG_CREATION_COUNT_KEY, String.valueOf(pkgCreationCount));
        messageMap.put(PKG_REUSE_COUNT_KEY, String.valueOf(pkgReuseCount));
        return messageMap;
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

    private Attachment makeAttachmentFromContent(AttachmentContent attachmentContent, int attachmentCount) {
        Attachment attachment = new Attachment();
        attachment.setAttachmentContentId(attachmentContent.getId());
        attachment.setAttachmentType(AttachmentType.SBOM);
        StringBuilder comment = new StringBuilder("Auto Generated: Used for importing CycloneDX SBOM || ").append(attachmentCount);
        attachment.setCreatedComment(comment.toString());
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

    private Component createComponent(org.cyclonedx.model.Component componentFromBom) {
        Component component = new Component();
        component.setName(getComponentName(componentFromBom).trim());
        component.setComponentType(ComponentType.OSS);
        return component;
    }

    private String getComponentName(org.cyclonedx.model.Component comp) {
        String name = CommonUtils.nullToEmptyString(comp.getName());
        if (CommonUtils.isNotNullEmptyOrWhitespace(comp.getGroup())) {
            return new StringBuilder(comp.getGroup()).append(DOT).append(name).toString();
        } else if (CommonUtils.isNotNullEmptyOrWhitespace(comp.getPublisher())) {
            return new StringBuilder(comp.getPublisher().replaceAll(CLEAN_PUBLISHER_REGEX, StringUtils.EMPTY)).append(DOT).append(name).toString();
        } else if (CommonUtils.isNotNullEmptyOrWhitespace(comp.getAuthor())) { //.replaceAll(CLEAN_PUBLISHER_REGEX, StringUtils.EMPTY)
            return new StringBuilder(comp.getAuthor().replaceAll(CLEAN_PUBLISHER_REGEX, StringUtils.EMPTY)).append(DOT).append(name).toString();
        } else {
            return name;
        }
    }

    private Release createRelease(org.cyclonedx.model.Component componentFromBom, Component component,
            Set<String> licenses) {
        Release release = new Release(component.getName(), CommonUtils.nullToEmptyString(componentFromBom.getVersion()).trim(), component.getId());
        release.setCreatorDepartment(user.getDepartment());
        if (release.isSetMainLicenseIds()) {
            release.getMainLicenseIds().addAll(licenses);
        } else {
            release.setMainLicenseIds(licenses);
        }
//        for (ExternalReference ref : componentFromBom.getExternalReferences()) {
//            if (ExternalReference.Type.VCS.equals(ref.getType())) {
//                release.setSourceCodeDownloadurl(ref.getUrl().replaceAll(VCS_HTTP_REGEX, HTTPS_SCHEME));
//            }
//        }
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

        for (ExternalReference ref : CommonUtils.nullToEmptyList(componentFromBom.getExternalReferences())) {
            if (ExternalReference.Type.WEBSITE.equals(ref.getType())) {
                pckg.setHomepageUrl(ref.getUrl());
            }
            if (ExternalReference.Type.VCS.equals(ref.getType())) {
                pckg.setVcs(ref.getUrl().replaceAll(VCS_HTTP_REGEX, HTTPS_SCHEME));
            }
        }

        if (pckg.isSetLicenseIds()) {
            pckg.getLicenseIds().addAll(licenses);
        } else {
            pckg.setLicenseIds(licenses);
        }
        return pckg;
    }
}

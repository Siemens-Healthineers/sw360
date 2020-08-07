/*
 * Copyright Siemens Healthineers GmBH, 2023. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made 
 * available under the terms of the Eclipse Public License 2.0 
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.portlets.packages;

import static com.google.common.base.Strings.nullToEmpty;
import static org.eclipse.sw360.datahandler.common.CommonUtils.nullToEmptySet;
import static org.eclipse.sw360.datahandler.common.SW360Utils.printName;
import static org.eclipse.sw360.portal.common.PortalConstants.ALL_USING_PROJECTS_COUNT;
import static org.eclipse.sw360.portal.common.PortalConstants.ATTACHMENT_CONTENT_ID;
import static org.eclipse.sw360.portal.common.PortalConstants.DOCUMENT_ID;
import static org.eclipse.sw360.portal.common.PortalConstants.IS_ERROR_IN_UPDATE_OR_CREATE;
import static org.eclipse.sw360.portal.common.PortalConstants.PACKAGE;
import static org.eclipse.sw360.portal.common.PortalConstants.PACKAGES_PORTLET_NAME;
import static org.eclipse.sw360.portal.common.PortalConstants.PACKAGE_ID;
import static org.eclipse.sw360.portal.common.PortalConstants.PACKAGE_LIST;
import static org.eclipse.sw360.portal.common.PortalConstants.PAGENAME;
import static org.eclipse.sw360.portal.common.PortalConstants.PAGENAME_DETAIL;
import static org.eclipse.sw360.portal.common.PortalConstants.PAGENAME_EDIT;
import static org.eclipse.sw360.portal.common.PortalConstants.PAGENAME_VIEW;
import static org.eclipse.sw360.portal.common.PortalConstants.PKG;
import static org.eclipse.sw360.portal.common.PortalConstants.RELEASE;
import static org.eclipse.sw360.portal.common.PortalConstants.USING_COMPONENTS;
import static org.eclipse.sw360.portal.common.PortalConstants.USING_PROJECTS;
import static org.eclipse.sw360.portal.common.PortalConstants.USING_RELEASE;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.Portlet;
import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestStatus;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestSummary;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.RequestSummary;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.components.ComponentService;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.packages.Package;
import org.eclipse.sw360.datahandler.thrift.packages.PackageService;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectService;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.portal.common.ChangeLogsPortletUtils;
import org.eclipse.sw360.portal.common.ErrorMessages;
import org.eclipse.sw360.portal.common.PortalConstants;
import org.eclipse.sw360.portal.common.PortletUtils;
import org.eclipse.sw360.portal.common.UsedAsLiferayAction;
import org.eclipse.sw360.portal.portlets.Sw360Portlet;
import org.eclipse.sw360.portal.portlets.projects.ProjectPortlet;
import org.eclipse.sw360.portal.users.UserCacheHolder;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import com.google.common.collect.ImmutableList;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.model.Layout;
import com.liferay.portal.kernel.portlet.LiferayPortletURL;
import com.liferay.portal.kernel.portlet.PortletURLFactoryUtil;
import com.liferay.portal.kernel.service.LayoutLocalServiceUtil;
import com.liferay.portal.kernel.servlet.SessionMessages;

@org.osgi.service.component.annotations.Component(
        immediate = true, 
        properties = {
                "/org/eclipse/sw360/portal/portlets/base.properties",
                "/org/eclipse/sw360/portal/portlets/default.properties" 
        }, property = {
                "javax.portlet.name=" + PACKAGES_PORTLET_NAME,
                "javax.portlet.display-name=Packages",
                "javax.portlet.info.short-title=Packages",
                "javax.portlet.info.title=Packages",
                "javax.portlet.resource-bundle=content.Language",
                "javax.portlet.init-param.view-template=/html/packages/view.jsp",
        },
        service = Portlet.class,
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
public class PackagePortlet extends Sw360Portlet {

    private static final Logger log = LogManager.getLogger(ProjectPortlet.class);

    private static final ImmutableList<Package._Fields> packageFilteredFields = ImmutableList.of(
            Package._Fields.NAME,
            Package._Fields.VERSION,
            Package._Fields.PACKAGE_MANAGER_TYPE,
            Package._Fields.LICENSE_IDS,
            Package._Fields.CREATED_BY,
            Package._Fields.CREATED_ON);

    // Helper methods
    private void addPackageBreadcrumb(RenderRequest request, RenderResponse response, Package pkg) {
        PortletURL url = response.createRenderURL();
        url.setParameter(PAGENAME, PAGENAME_DETAIL);
        url.setParameter(PACKAGE_ID, pkg.getId());

        addBreadcrumbEntry(request, pkg.getName(), url);
    }

    // ! Serve resource and helpers
    @Override
    public void serveResource(ResourceRequest request, ResourceResponse response) throws IOException, PortletException {

        final var action = request.getParameter(PortalConstants.ACTION);

        PackageService.Iface packageClient = thriftClients.makePackageClient();
        if (PortalConstants.IMPORT_CYCLONEDX_SBOM.equals(action)) {
            importCycloneDxBoM(request, response);
        } else if (isGenericAction(action)) {
            dealWithGenericAction(request, response, action);
        } else if (PortalConstants.VIEW_LINKED_RELEASES.equals(action)) {
            serveLinkedReleases(request, response);
        } else if (PortalConstants.LOAD_CHANGE_LOGS.equals(action) || PortalConstants.VIEW_CHANGE_LOGS.equals(action)) {
            ChangeLogsPortletUtils changeLogsPortletUtilsPortletUtils = PortletUtils
                    .getChangeLogsPortletUtils(thriftClients);
            JSONObject dataForChangeLogs = changeLogsPortletUtilsPortletUtils.serveResourceForChangeLogs(request,
                    response, action);
            writeJSON(request, response, dataForChangeLogs);
        } else if (PortalConstants.DELETE_PACKAGE.equals(action)) {
            serveDeletePackage(request, response);
        }
    }

    // ! VIEW and helpers
    @Override
    public void doView(RenderRequest request, RenderResponse response) throws IOException, PortletException {

        final var pageName = request.getParameter(PAGENAME);
        final var id = request.getParameter("id");
        final var packageId = request.getParameter(PACKAGE_ID);
        if (PAGENAME_DETAIL.equals(pageName)) {
            prepareDetailView(request, response);
            include("/html/packages/detailPackage.jsp", request, response);
        } else if (PAGENAME_EDIT.equals(pageName)) {
            preparePackageEdit(request);
            include("/html/packages/editPackage.jsp", request, response);
        } else {
            prepareStandardView(request);
            super.doView(request, response);
        }
    }

    private void serveLinkedReleases(ResourceRequest request, ResourceResponse response)
            throws IOException, PortletException {
        String what = request.getParameter(PortalConstants.WHAT);

        if (PortalConstants.RELEASE_SEARCH.equals(what)) {
            String where = request.getParameter(PortalConstants.WHERE);
            serveReleaseSearchResults(request, response, where);
        }
    }

    private void serveReleaseSearchResults(ResourceRequest request, ResourceResponse response, String searchText)
            throws IOException, PortletException {
        request.setAttribute("isSingleRelease", "true");
        serveReleaseSearch(request, response, searchText);
    }

    private void prepareStandardView(RenderRequest request) {
        List<Package> packageList;
        try {
            PackageService.Iface packageClient = thriftClients.makePackageClient();
            packageList = packageClient.getAllPackages();
        } catch (TException e) {
            log.error("Could not get Packages from backend ", e);
            packageList = Collections.emptyList();
        }
        request.setAttribute(PACKAGE_LIST, packageList);
    }

    private void importCycloneDxBoM(ResourceRequest request, ResourceResponse response) {
        PackageService.Iface packageClient = thriftClients.makePackageClient();
        final User user = UserCacheHolder.getUserFromRequest(request);
        String attachmentContentId = request.getParameter(ATTACHMENT_CONTENT_ID);

        try {
            final RequestSummary requestSummary = packageClient.importCycloneDxFromAttachmentContent(user,
                    attachmentContentId);

            LiferayPortletURL projectUrl = getProjectPortletUrl(request, requestSummary.getMessage());

            JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
            jsonObject.put("redirectUrl", projectUrl.toString());

            renderRequestSummary(request, response, requestSummary, jsonObject);
        } catch (TException e) {
            log.error("Failed to import Package BOM.", e);
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE,
                    Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
        }
    }

    private static LiferayPortletURL getProjectPortletUrl(PortletRequest request, String projectId) {
        Optional<Layout> layout = LayoutLocalServiceUtil.getLayouts(QueryUtil.ALL_POS, QueryUtil.ALL_POS).stream()
                .filter(l -> ("/" + PortalConstants.PROJECTS.toLowerCase()).equals(l.getFriendlyURL())).findFirst();
        if (layout.isPresent()) {
            long plId = layout.get().getPlid();
            LiferayPortletURL projectUrl = PortletURLFactoryUtil.create(request, PortalConstants.PROJECT_PORTLET_NAME,
                    plId, PortletRequest.RENDER_PHASE);
            projectUrl.setParameter(PortalConstants.PROJECT_ID, projectId);
            projectUrl.setParameter(PortalConstants.PAGENAME, PortalConstants.PAGENAME_DETAIL);
            return projectUrl;
        }
        return null;
    }

    private void prepareDetailView(RenderRequest request, RenderResponse response) {
        String id = request.getParameter(PACKAGE_ID);
        request.setAttribute(DOCUMENT_ID, id);
        final User user = UserCacheHolder.getUserFromRequest(request);
        PackageService.Iface packageClient = thriftClients.makePackageClient();
        Package pkg;
        try {
            pkg = packageClient.getPackageById(id);
            request.setAttribute(PKG, pkg);
            Release release = null;
            if (CommonUtils.isNotNullEmptyOrWhitespace(pkg.getReleaseId())) {
                ComponentService.Iface compClient = thriftClients.makeComponentClient();
                release = compClient.getReleaseById(pkg.getReleaseId(), user);
                request.setAttribute(RELEASE, release);
            }
            setUsingDocs(request, pkg.getId(), user);
            String releaseName = SW360Utils.printFullname(release);
            request.setAttribute("releaseName", releaseName);
        } catch (TException e) {
            e.printStackTrace();
        }
    }

    private void preparePackageEdit(RenderRequest request) {
        String id = request.getParameter(PACKAGE_ID);
        final User user = UserCacheHolder.getUserFromRequest(request);
        PackageService.Iface packageClient = thriftClients.makePackageClient();
        Package pkg = (Package) request.getAttribute(PKG);
        try {
            if (id == null) {
                pkg = new Package();
            } else {
                if (pkg == null) {
                    pkg = packageClient.getPackageById(id);
                }
                Release release = null;
                if (CommonUtils.isNotNullEmptyOrWhitespace(pkg.getReleaseId())) {
                    ComponentService.Iface compClient = thriftClients.makeComponentClient();
                    release = compClient.getReleaseById(pkg.getReleaseId(), user);
                    request.setAttribute(RELEASE, release);
                }
                String releaseName = SW360Utils.printFullname(release);
                request.setAttribute("releaseName", releaseName);
            }
            setUsingDocsCount(request, id, user);
            request.setAttribute(PKG, pkg);
        } catch (TException e) {
            e.printStackTrace();
        }
    }

    @UsedAsLiferayAction
    public void updatePackage(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        String packageId = request.getParameter(PACKAGE_ID);
        User user = UserCacheHolder.getUserFromRequest(request);
        PackageService.Iface packageClient = thriftClients.makePackageClient();
        Package pkg;
        RequestStatus requestStatus;
        try {
            if (packageId != null) {
                pkg = packageClient.getPackageById(packageId);
                PackagePortletUtils.updatePackageFromRequest(request, pkg);
                requestStatus = packageClient.updatePackage(pkg, user);
                setSessionMessage(request, requestStatus, PACKAGE, "update", printName(pkg));
                if (RequestStatus.DUPLICATE.equals(requestStatus) || RequestStatus.NAMINGERROR.equals(requestStatus)) {
                    if (RequestStatus.DUPLICATE.equals(requestStatus))
                        setSW360SessionError(request, ErrorMessages.PACKAGE_DUPLICATE);
                    else if (RequestStatus.NAMINGERROR.equals(requestStatus))
                        setSW360SessionError(request, ErrorMessages.PACKAGE_NAME_VERSION_ERROR);
                    else if (RequestStatus.INVALID_INPUT.equals(requestStatus))
                        setSW360SessionError(request, ErrorMessages.INVALID_PURL);

                    response.setRenderParameter(PAGENAME, PAGENAME_EDIT);
                    response.setRenderParameter(PACKAGE_ID, packageId);
                    prepareRequestForEditAfterError(request, pkg, user);
                } else {
                    response.setRenderParameter(PAGENAME, PAGENAME_DETAIL);
                    response.setRenderParameter(PACKAGE_ID, packageId);
                    request.setAttribute(PKG, pkg);
                }
            } else {
                // Add new Package
                pkg = new Package();
                PackagePortletUtils.updatePackageFromRequest(request, pkg);
                AddDocumentRequestSummary summary = packageClient.addPackage(pkg, user);
                AddDocumentRequestStatus status = summary.getRequestStatus();

                if (AddDocumentRequestStatus.DUPLICATE.equals(status)
                        || AddDocumentRequestStatus.NAMINGERROR.equals(status)) {
                    if (AddDocumentRequestStatus.DUPLICATE.equals(status))
                        setSW360SessionError(request, ErrorMessages.PACKAGE_DUPLICATE);
                    else if (AddDocumentRequestStatus.NAMINGERROR.equals(status))
                        setSW360SessionError(request, ErrorMessages.PACKAGE_NAME_VERSION_ERROR);
                    else if (AddDocumentRequestStatus.INVALID_INPUT.equals(status))
                        setSW360SessionError(request, ErrorMessages.INVALID_PURL);

                    response.setRenderParameter(PAGENAME, PAGENAME_EDIT);
                    //response.setRenderParameter(PACKAGE_ID, packageId);
                    prepareRequestForEditAfterError(request, pkg, user);
                } else if (AddDocumentRequestStatus.SUCCESS.equals(status)) {
                    String successMsg = "Package " + printName(pkg) + " added successfully";
                    SessionMessages.add(request, "request_processed", successMsg);
                    response.setRenderParameter(PACKAGE_ID, summary.getId());
                    response.setRenderParameter(PAGENAME, PAGENAME_EDIT);
                    pkg.setId(summary.getId());
                    request.setAttribute(PKG, pkg);
                } else {
                    setSW360SessionError(request, ErrorMessages.PACKAGE_NOT_ADDED);
                    response.setRenderParameter(PAGENAME, PAGENAME_VIEW);
                }
            }
        } catch (TException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @UsedAsLiferayAction
    public void deletePackage(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        User user = UserCacheHolder.getUserFromRequest(request);
        RequestStatus requestStatus = PackagePortletUtils.deletePackage(request, user, log);
        setSessionMessage(request, requestStatus, "Package", "delete");
        response.setRenderParameter(PAGENAME, PAGENAME_VIEW);
    }

    @UsedAsLiferayAction
    public void applyFilters(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        for (Package._Fields componentFilteredField : packageFilteredFields) {
            response.setRenderParameter(componentFilteredField.toString(), nullToEmpty(request.getParameter(componentFilteredField.toString())));
        }
        response.setRenderParameter(PortalConstants.DATE_RANGE, nullToEmpty(request.getParameter(PortalConstants.DATE_RANGE)));
        response.setRenderParameter(PortalConstants.END_DATE, nullToEmpty(request.getParameter(PortalConstants.END_DATE)));
        response.setRenderParameter(PortalConstants.EXACT_MATCH_CHECKBOX, nullToEmpty(request.getParameter(PortalConstants.EXACT_MATCH_CHECKBOX)));
    }


    private void setUsingDocs(RenderRequest request, String packageId, User user) throws TException {
        Set<Project> usingProjects = null;
        int allUsingProjectsCount = 0;
        if (CommonUtils.isNotNullEmptyOrWhitespace(packageId)) {
            ProjectService.Iface projectClient = thriftClients.makeProjectClient();
            usingProjects = projectClient.searchProjectByPackageId(packageId, user);
            allUsingProjectsCount = projectClient.getProjectCountByPackageId(packageId);
        }
        request.setAttribute(USING_PROJECTS, nullToEmptySet(usingProjects));
        request.setAttribute(ALL_USING_PROJECTS_COUNT, allUsingProjectsCount);
    }

    private void setUsingDocsCount(RenderRequest request, String packageId, User user) throws TException {
        int allUsingProjectsCount = 0;
        if (CommonUtils.isNotNullEmptyOrWhitespace(packageId)) {
            ProjectService.Iface projectClient = thriftClients.makeProjectClient();
            allUsingProjectsCount = projectClient.getProjectCountByPackageId(packageId);
        }
        request.setAttribute(ALL_USING_PROJECTS_COUNT, allUsingProjectsCount);
    }

    private void prepareRequestForEditAfterError(ActionRequest request, Package pkg, User user) throws TException {
        request.setAttribute(PKG, pkg);
        request.setAttribute(IS_ERROR_IN_UPDATE_OR_CREATE, true);
    }

    private void serveDeletePackage(ResourceRequest request, ResourceResponse response) throws IOException {
        RequestStatus requestStatus = removePackage(request);
        serveRequestStatus(request, response, requestStatus, "Problem removing package", log);
    }

    private RequestStatus removePackage(PortletRequest request) {
        final User user = UserCacheHolder.getUserFromRequest(request);
        return PackagePortletUtils.deletePackage(request, user, log);
    }

    private void prepareRequestForReleaseEditAfterDuplicateError(ActionRequest request, Package pkg) throws TException {
        request.setAttribute("pkg", pkg);
    }

    // @Override
    // protected void dealWithFossologyAction(ResourceRequest request, ResourceResponse response, String action)
    // throws IOException, PortletException {
    // throw new UnsupportedOperationException("cannot call this action on the package portlet");
    // }

//    @Override
//    protected Set<Attachment> getAttachments(String documentId, String documentType, User user) {
//        // throw new UnsupportedOperationException("cannot call this getAttachments action on the package portlet!");
//        return Collections.emptySet();
//    }
}

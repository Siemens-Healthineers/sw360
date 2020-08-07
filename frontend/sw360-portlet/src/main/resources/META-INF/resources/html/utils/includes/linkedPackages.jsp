<%--
  ~ Copyright Siemens Healthineers GmBH, 2023. Part of the SW360 Portal Project.
  ~
  ~ This program and the accompanying materials are made
  ~ available under the terms of the Eclipse Public License 2.0
  ~ which is available at https://www.eclipse.org/legal/epl-2.0/
  ~
  ~ SPDX-License-Identifier: EPL-2.0
--%>

<%@include file="/html/init.jsp" %>
<%-- the following is needed by liferay to display error messages--%>
<%@ include file="/html/utils/includes/errorKeyToMessage.jspf"%>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="tags" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<%@ page import="com.liferay.portal.kernel.portlet.PortletURLFactoryUtil" %>
<%@ page import="org.eclipse.sw360.datahandler.thrift.projects.Project" %>
<%@ page import="javax.portlet.PortletRequest" %>
<%@ page import="org.eclipse.sw360.portal.common.PortalConstants"%>

<jsp:useBean id="packages" type="java.util.Set<org.eclipse.sw360.datahandler.thrift.packages.Package>" class="java.util.HashSet" scope="request" />

<portlet:defineObjects/>
<liferay-theme:defineObjects/>

<liferay-portlet:renderURL var="friendlyPackageURL" portletName="sw360_portlet_packages">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>"/>
    <portlet:param name="<%=PortalConstants.PACKAGE_ID%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>"/>
</liferay-portlet:renderURL>

<liferay-portlet:renderURL var="friendlyLicenseURL" portletName="sw360_portlet_licenses">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>"/>
    <portlet:param name="<%=PortalConstants.LICENSE_ID%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>"/>
</liferay-portlet:renderURL>

<table id="linkedPackagesTable" class="table table-bordered"></table>
<%--for javascript library loading --%>
<%@ include file="/html/utils/includes/requirejs.jspf" %>

<script>
    AUI().use('liferay-portlet-url', function () {
        var PortletURL = Liferay.PortletURL;

        require(['jquery', 'modules/autocomplete', 'modules/dialog', 'bridges/datatables', 'utils/render' ], function($, autocomplete, dialog, datatables, render) {

            var tableData = [],
                licenses;
            <core_rt:forEach items="${packages}" var="pkg">
                licenses = [];
                <c:set var = "mainLicenseIds" value = "" />

                <core_rt:if test="${not empty pkg.licenseIds}">
                <core_rt:forEach items="${pkg.licenseIds}" var="license">
                    licenses.push("<sw360:out value='${license}'/>");
                </core_rt:forEach >
                </core_rt:if>

            	tableData.push({
                    "DT_RowId": "${pkg.id}",
                    "0": "",
                    "1": "${pkg.name} (${pkg.version})",
                    "2": "${pkg.licenseIds}",
                    "3": "<sw360:DisplayEnum value="${pkg.packageManagerType}"/>",
                    "4": ""
                });
            </core_rt:forEach>
            var packagesTable = createPackagesTable();
            // create and render data table
            function createPackagesTable() {
                let columns = [
                    {"title": "<liferay-ui:message key="vendor" />", width: "15%"},
                    {"title": "Package Name", render: {display: renderPackageNameLink}, width: "50%"},
                    {"title": "<liferay-ui:message key="main.licenses" />", render: {display: renderLicenseLink}, width: "20%"},
                    {"title": "Package Type", width: "15%"},
                    {"title": "<liferay-ui:message key="actions" />", render: {display: renderPackageActions}, className: 'one action', orderable: false, width: "10%"}
                ];
                let printColumns = [0, 1, 2, 3];
                var packagesTable = datatables.create('#linkedPackagesTable', {
                    searching: true,
                    deferRender: false, // do not change this value
                    data: tableData,
                    columns: columns,
                    initComplete: datatables.showPageContainer,
                    language: {
                        url: "<liferay-ui:message key="datatables.lang" />",
                        loadingRecords: "<liferay-ui:message key="loading" />"
                    },
                    order: [
                        [1, 'asc']
                    ]
                }, printColumns, undefined, true);

                return packagesTable;
            }

            function renderLicenseLink(lics, type, row) {
            	lics = lics.replace(/[\[\]]+/g,'');
                if (!lics || !lics.length) {
                    return "";
                }
                lics = lics.split(",");
                var links = [],
                    licensePortletURL = '<%=friendlyLicenseURL%>'
                    .replace(/components|releases|projects/g, "licenses");// DIRTY WORKAROUND

                for (var i = 0; i < lics.length; i++) {
                    links[i] = render.linkTo(replaceFriendlyUrlParameter(licensePortletURL.toString(), lics[i], '<%=PortalConstants.PAGENAME_DETAIL%>'), lics[i]);
                }

                if(type == 'display') {
                    return links.join(', ');
                } else if(type == 'print') {
                    return lics.join(', ');
                } else if(type == 'type') {
                    return 'string';
                } else {
                    return lics.join(', ');
                }
            }
            
            function renderPackageNameLink(data, type, row) {
                return render.linkTo(replaceFriendlyUrlParameter('<%=friendlyPackageURL%>'.replace(/projects|components|releases/g, "packages"), row.DT_RowId, '<%=PortalConstants.PAGENAME_DETAIL%>'), row[1]);
            }

            // helper functions
            function makePackageUrl(packageId, page) {
                var portletURL = PortletURL.createURL('<%= PortletURLFactoryUtil.create(request, portletDisplay.getId(), themeDisplay.getPlid(), PortletRequest.RENDER_PHASE) %>'.replace(/projects|components|releases/g, "packages"))
                    .setParameter('<%=PortalConstants.PAGENAME%>', page)
                    .setParameter('<%=PortalConstants.PACKAGE_ID%>', packageId);
                return portletURL.toString();
            }

            function renderPackageActions(data, type, row) {
                var $actions = $('<div>', {
                    'class': 'actions'
                    }),
                    $editAction = render.linkTo(
                            makePackageUrl(row.DT_RowId, '<%=PortalConstants.PAGENAME_EDIT%>'),
                            "",
                            '<svg class="lexicon-icon"><title><liferay-ui:message key="edit" /></title><use href="/o/org.eclipse.sw360.liferay-theme/images/clay/icons.svg#pencil"/></svg>'
                        );
                    $deleteAction = $('<svg>', {
                        'class': 'delete lexicon-icon',
                    });

                $deleteAction.append($('<title>Delete</title><use href="/o/org.eclipse.sw360.liferay-theme/images/clay/icons.svg#trash"/>'));

                $actions.append($editAction, $deleteAction);
                return $actions[0].outerHTML;
            }

            function replaceFriendlyUrlParameter(portletUrl, id, page) {
                return portletUrl
                    .replace('<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>', page)
                    .replace('<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>', id);
            }
        });
    });
</script>
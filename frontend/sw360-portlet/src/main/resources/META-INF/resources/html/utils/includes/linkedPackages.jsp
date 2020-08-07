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
<portlet:defineObjects/>
<liferay-theme:defineObjects/>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="tags" tagdir="/WEB-INF/tags" %>

<%@ page import="com.liferay.portal.kernel.portlet.PortletURLFactoryUtil" %>
<%@ page import="javax.portlet.PortletRequest" %>
<%@ page import="org.eclipse.sw360.portal.common.PortalConstants"%>

<jsp:useBean id="packages" type="java.util.Set<org.eclipse.sw360.datahandler.thrift.packages.Package>" class="java.util.HashSet" scope="request" />
<core_rt:set var="portletName" value="<%=themeDisplay.getPortletDisplay().getPortletName() %>"/>

<liferay-portlet:renderURL var="friendlyPackageURL" portletName="sw360_portlet_packages">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>"/>
    <portlet:param name="<%=PortalConstants.PACKAGE_ID%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>"/>
</liferay-portlet:renderURL>

<liferay-portlet:renderURL var="friendlyLicenseURL" portletName="sw360_portlet_licenses">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>"/>
    <portlet:param name="<%=PortalConstants.LICENSE_ID%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>"/>
</liferay-portlet:renderURL>

<portlet:resourceURL var="deletePackageURL">
    <portlet:param name="<%=PortalConstants.ACTION%>" value='<%=PortalConstants.DELETE_PACKAGE%>'/>
</portlet:resourceURL>

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
                <core_rt:choose>
                <core_rt:when test="${portletName == 'sw360_portlet_projects'}">
                    "2": "WIP... Release",
                    "3": "WIP... RCS",
                    "4": "${pkg.licenseIds}",
                    "5": "<sw360:DisplayEnum value='${pkg.packageManagerType}'/>",
                    "6": ""
                </core_rt:when>
                <core_rt:otherwise>
                    "2": "${pkg.licenseIds}",
                    "3": "<sw360:DisplayEnum value='${pkg.packageManagerType}'/>",
                    "4": ""
                </core_rt:otherwise>
                </core_rt:choose>
            });
        </core_rt:forEach>
        var packagesTable = createPackagesTable();
        // create and render data table
        <core_rt:choose>
        <core_rt:when test="${portletName == 'sw360_portlet_projects'}">
            function createPackagesTable() {
                let columns = [
                    {"title": "<liferay-ui:message key='vendor' />", width: "15%"},
                    {"title": "<liferay-ui:message key='package.name' />", render: {display: renderPackageNameLink}, width: "30%"},
                    {"title": "<liferay-ui:message key='release.name' />", render: {display: renderReleaseNameLink}, width: "20%"},
                    {"title": "<liferay-ui:message key='release.clearing.state' />", render: {display: renderReleaseCS}, width: "5%"},
                    {"title": "<liferay-ui:message key='licenses' />", render: {display: renderLicenseLink}, width: "20%"},
                    {"title": "<liferay-ui:message key='package.manager' />", width: "7%"},
                    {"title": "<liferay-ui:message key='actions' />", render: {display: renderPackageActions}, className: 'one action', orderable: false, width: "2%"}
                ];
                let printColumns = [0, 1, 2, 3, 4, 5];
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
        </core_rt:when>
        <core_rt:otherwise>
            function createPackagesTable() {
                let columns = [
                    {"title": "<liferay-ui:message key='vendor' />", width: "15%"},
                    {"title": "<liferay-ui:message key='package.name' />", render: {display: renderPackageNameLink}, width: "45%"},
                    {"title": "<liferay-ui:message key='licenses' />", render: {display: renderLicenseLink}, width: "20%"},
                    {"title": "<liferay-ui:message key='package.manager' />", width: "15%"},
                    {"title": "<liferay-ui:message key='actions' />", render: {display: renderPackageActions}, className: 'one action', orderable: false, width: "5%"}
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
        </core_rt:otherwise>
        </core_rt:choose>

        function renderReleaseCS(relCS, type, row) {
            return row[3];
        }

        function renderReleaseNameLink(releaseName, type, row, meta) {
            return row[2];
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

        function replaceFriendlyUrlParameter(portletUrl, id, page) {
            return portletUrl
                .replace('<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>', page)
                .replace('<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>', id);
        }

        function renderPackageActions(data, type, row) {
            var $actions = $('<div>', {
                'class': 'actions'
                }),
                $editAction = render.linkTo(
                        makePackageUrl(row.DT_RowId, '<%=PortalConstants.PAGENAME_EDIT%>'),
                        "",
                        '<svg class="lexicon-icon"><title><liferay-ui:message key="edit.package" /></title><use href="/o/org.eclipse.sw360.liferay-theme/images/clay/icons.svg#pencil"/></svg>'
                    );
            // don't add delete button for project details page
            if (!row[5]) {
                $deleteAction = $('<svg>', {
                    'class': 'delete lexicon-icon',
                    'data-package-id': row.DT_RowId,
                    'data-package-name': row[1],
                });
                $deleteAction.append($('<title><liferay-ui:message key="delete.package" /></title><use href="/o/org.eclipse.sw360.liferay-theme/images/clay/icons.svg#trash"/>'));
                $actions.append($editAction, $deleteAction);
            } else {
                $actions.append($editAction);
            }
            return $actions[0].outerHTML;
        }

        $('#linkedPackagesTable').on('click', 'svg.delete', function(event) {
            var data = $(event.currentTarget).data();
            deletePackage(data.packageId, data.packageName);
        });

        // Delete package action
        function deletePackage(id, name) {
            var $dialog;

            function deletePackageInternal(callback) {
                jQuery.ajax({
                    type: 'POST',
                    url: '<%=deletePackageURL%>',
                    cache: false,
                    data: {
                        <portlet:namespace/><%=PortalConstants.PACKAGE_ID%>: id
                    },
                    success: function (data) {
                        callback();

                        if (data.result == 'SUCCESS') {
                            packagesTable.row('#' + id).remove().draw(false);
                            $dialog.info('<liferay-ui:message key="deleted.successfully" />', true);
                            setTimeout(function() {
                                $dialog.close();
                            }, 5000);
                        } else if (data.result == 'SENT_TO_MODERATOR') {
                            $dialog.info('<liferay-ui:message key="you.may.not.delete.the.package.but.a.request.was.sent.to.a.moderator" />', true);
                        } else if (data.result == 'IN_USE') {
                            $dialog.warning('<liferay-ui:message key="i.could.not.delete.the.package.since.it.is.used.by.another.project.please.unlink.it.before.deleting" />');
                        } else if (data.result == 'ACCESS_DENIED') {
                            $dialog.warning('<liferay-ui:message key="access.denied" />: <liferay-ui:message key="you.do.not.have.permission.to.delete.the.package" />!');
                        } else {
                            $dialog.alert('<liferay-ui:message key="i.could.not.delete.the.package" />');
                        }
                    },
                    error: function () {
                        callback();
                        $dialog.alert('<liferay-ui:message key="i.could.not.delete.the.package" />');
                    }
                });
            }

                $dialog = dialog.confirm(
                    'danger',
                    'question-circle',
                    '<liferay-ui:message key="delete.package" />?',
                        '<p><liferay-ui:message key="do.you.really.want.to.delete.the.package.x" /></p>',
                    '<liferay-ui:message key="delete.package" />',
                    {
                        name: name
                    }, function(submit, callback) {
                        deletePackageInternal(callback);
                    }
                );
        }
    });
});
</script>
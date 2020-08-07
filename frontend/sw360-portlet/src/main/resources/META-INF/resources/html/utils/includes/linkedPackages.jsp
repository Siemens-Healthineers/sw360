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

<core_rt:set var="portletName" value="<%=themeDisplay.getPortletDisplay().getPortletName() %>"/>

<liferay-portlet:renderURL var="friendlyPackageURL" portletName="sw360_portlet_packages">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>"/>
    <portlet:param name="<%=PortalConstants.PACKAGE_ID%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>"/>
</liferay-portlet:renderURL>

<liferay-portlet:renderURL var="friendlyLicenseURL" portletName="sw360_portlet_licenses">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>"/>
    <portlet:param name="<%=PortalConstants.LICENSE_ID%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>"/>
</liferay-portlet:renderURL>

<liferay-portlet:renderURL var="friendlyReleaseURL" portletName="sw360_portlet_components">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>"/>
    <portlet:param name="<%=PortalConstants.RELEASE_ID%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>"/>
</liferay-portlet:renderURL>

<portlet:resourceURL var="deletePackageURL">
    <portlet:param name="<%=PortalConstants.ACTION%>" value='<%=PortalConstants.DELETE_PACKAGE%>'/>
</portlet:resourceURL>

<portlet:resourceURL var="loadLinkedPackages">
    <portlet:param name="<%=PortalConstants.ACTION%>" value="<%=PortalConstants.LOAD_LINKED_PACKAGES%>"/>
    <portlet:param name="<%=PortalConstants.DOCUMENT_ID%>" value="${docid}"/>
</portlet:resourceURL>

<div id="linkedPackagesSpinner">
    <%@ include file="/html/utils/includes/pageSpinner.jspf" %>
</div>
<table id="linkedPackagesTable" class="table table-bordered d-none"></table>
<%--for javascript library loading --%>
<%@ include file="/html/utils/includes/requirejs.jspf" %>

<script>
AUI().use('liferay-portlet-url', function () {
    var PortletURL = Liferay.PortletURL;

    require(['jquery', 'modules/autocomplete', 'modules/dialog', 'bridges/datatables', 'utils/render' ], function($, autocomplete, dialog, datatables, render) {

        let isProjectPortlet = '${portletName}' === 'sw360_portlet_projects',
            isComponentPortlet = '${portletName}' === 'sw360_portlet_components',
            isPackagesPresent = isProjectPortlet ? ${project.getPackageIdsSize() > 0} : ${release.getPackageIdsSize() > 0};

        if (isPackagesPresent) {
            var loadLinkedPackagesUrl = '<%=loadLinkedPackages%>';
            $.ajax({url: loadLinkedPackagesUrl,
                type: 'GET',
                dataType: 'json'
            }).done(function(result) {
                createLinkedPackagesTable(result);
                $("#linkedPackagesSpinner").addClass("d-none");
                $("#linkedPackagesTable").removeClass("d-none");
            });
        } else {
            let dummyData = {data: ''};
            createLinkedPackagesTable(dummyData);
            $("#linkedPackagesSpinner").addClass("d-none");
            $("#linkedPackagesTable").removeClass("d-none");
        }

        var linkedPackagesTable;
        function createLinkedPackagesTable(packagesJsonData) {
            if (isProjectPortlet) {
                var columns = [
                    {title: "<liferay-ui:message key='vendor' />", data : "vendor", defaultContent: "", width: "15%" },
                    {title: "<liferay-ui:message key='package.name.with.version' />", data : "name", render: {display: renderPackageNameLink}, width: "30%" },
                    {title: "<liferay-ui:message key='release.name' />", data : "relName", defaultContent: "", render: {display: renderReleaseNameLink}, width: "20%"},
                    {title: "<liferay-ui:message key='release.clearing.state' />", data : "relCS", defaultContent: "", render: {display: renderReleaseCS}, width: "10%" },
                    {title: "<liferay-ui:message key='licenses' />", data: "lics", defaultContent: "", render: {display: renderLicenseLink}, width: "15%" },
                    {title: "<liferay-ui:message key='package.manager' />", data: "pkgMgrType", width: "7%"},
                    {title: "<liferay-ui:message key='actions' />", data: 'DT_RowId', render: {display: renderPackageActions}, orderable: false, className: 'one action', width: "2%"}
                ],
                printColumns = [0, 1, 2, 3, 4, 5];
            } else {
                var columns = [
                    {title: "<liferay-ui:message key='vendor' />", data : "vendor", defaultContent: "", width: "20%" },
                    {title: "<liferay-ui:message key='package.name.with.version' />", data : "name", render: {display: renderPackageNameLink}, width: "40%" },
                    {title: "<liferay-ui:message key='licenses' />", data: "lics", defaultContent: "", render: {display: renderLicenseLink}, width: "25%" },
                    {title: "<liferay-ui:message key='package.manager' />", data: "pkgMgrType", width: "10%"},
                    {title: "<liferay-ui:message key='actions' />", data: 'DT_RowId', render: {display: renderPackageActions}, orderable: false, className: 'two actions', width: "5%"}
                ],
                printColumns = [0, 1, 2, 3];
            }
            linkedPackagesTable = datatables.create('#linkedPackagesTable', {
                data: packagesJsonData.data,
                infoOnTop: true,
                columns: columns,
                columnDefs: [],
                "order": [[ 1, "asc" ]],
                fnDrawCallback: datatables.showPageContainer,
                language: {
                    url: "<liferay-ui:message key="datatables.lang" />",
                    loadingRecords: "<liferay-ui:message key="loading" />"
                },
                initComplete: datatables.showPageContainer,
            }, printColumns, undefined, true);
        }

        function renderReleaseNameLink(releaseName, type, row, meta) {
            var releasePortletURL = '<%=friendlyReleaseURL%>'
                .replace(/projects|components|releases/g, "components"); // DIRTY WORKAROUND
            if (releaseName) {
                return render.linkTo(replaceFriendlyUrlParameter(releasePortletURL.toString(), row.relId, '<%=PortalConstants.PAGENAME_RELEASE_DETAIL%>'), releaseName);
            } else {
                return '<liferay-ui:message key="no.linked.release" />';
            }
        }

        function renderReleaseCS(relCS, type, row) {
            var $state = $('<div>', {
                'class': 'content-center'
            });
            if (relCS) {
                let backgroundColour = getReleaseClearingStateBackgroundColour(relCS);
                var $csBox = $('<div>', {
                    'class': 'stateBox capsuleLeft capsuleRight ' + backgroundColour
                }).text('CS').attr("title", "Release clearing state: " + relCS );
                $state.append($csBox);
            } else {
                $state.text('<liferay-ui:message key="not.applicable" />');
            }
            return $state[0].outerHTML;
        }

        function getReleaseClearingStateBackgroundColour(relCS) {
            switch (relCS) {
                case 'Report approved': // -> green
                    return '<%=PortalConstants.CLEARING_STATE_CLOSED__CSS%>';
                case 'Report available': // -> blue
                    return 'bg-info';
                case 'Scan available': // -> orange
                case 'Sent to clearing tool':
                    return 'bg-primary';
                case 'New': // -> red
                    return '<%=PortalConstants.CLEARING_STATE_OPEN__CSS%>';
                case 'Under clearing': // -> yellow
                    return '<%=PortalConstants.CLEARING_STATE_INPROGRESS__CSS%>';
            }
            return '<%=PortalConstants.CLEARING_STATE_UNKNOWN__CSS%>';
        }

        function renderLicenseLink(lics, type, row) {
            var links = [],
                licensePortletURL = '<%=friendlyLicenseURL%>'
                .replace(/components|releases|projects/g, "licenses");// DIRTY WORKAROUND

            for (var i = 0; i < lics.length; i++) {
                links[i] = render.linkTo(replaceFriendlyUrlParameter(licensePortletURL.toString(), lics[i], '<%=PortalConstants.PAGENAME_DETAIL%>'), lics[i]);
            }

            if (type == 'display') {
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
            return render.linkTo(replaceFriendlyUrlParameter('<%=friendlyPackageURL%>'.replace(/projects|components|releases/g, "packages"), row.DT_RowId, '<%=PortalConstants.PAGENAME_DETAIL%>'), data);
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
            if (isComponentPortlet) {
                $deleteAction = $('<svg>', {
                    'class': 'delete lexicon-icon',
                    'data-package-id': row.DT_RowId,
                    'data-package-name': row.name,
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
                            linkedPackagesTable.row('#' + id).remove().draw(false);
                            $dialog.close();
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
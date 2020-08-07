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

<%@ page import="com.liferay.portal.kernel.portlet.PortletURLFactoryUtil" %>
<%@ page import="javax.portlet.PortletRequest" %>
<%@ page import="org.eclipse.sw360.datahandler.thrift.DateRange" %>
<%@ page import="org.eclipse.sw360.datahandler.thrift.packages.Package" %>
<%@ page import="org.eclipse.sw360.datahandler.thrift.packages.PackageManagerType" %>
<%@ page import="org.eclipse.sw360.portal.common.PortalConstants" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="tags" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<portlet:defineObjects/>
<liferay-theme:defineObjects/>
<jsp:useBean id="packageList" type="java.util.List<org.eclipse.sw360.datahandler.thrift.packages.Package>" class="java.util.ArrayList" scope="request" />

<portlet:renderURL var="friendlyPackageURL">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>"/>
    <portlet:param name="<%=PortalConstants.PACKAGE_ID%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>"/>
</portlet:renderURL>

<liferay-portlet:renderURL var="friendlyLicenseURL" portletName="sw360_portlet_licenses">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>"/>
    <portlet:param name="<%=PortalConstants.LICENSE_ID%>" value="<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>"/>
</liferay-portlet:renderURL>

<portlet:resourceURL var="packagesURL">
    <portlet:param name="<%=PortalConstants.ACTION%>" value='<%=PortalConstants.LOAD_COMPONENT_LIST%>'/>
</portlet:resourceURL>

<portlet:renderURL var="addPackageURL">
    <portlet:param name="<%=PortalConstants.PAGENAME%>" value="<%=PortalConstants.PAGENAME_EDIT%>"/>
</portlet:renderURL>

<portlet:resourceURL var="deletePackageURL">
    <portlet:param name="<%=PortalConstants.ACTION%>" value='<%=PortalConstants.DELETE_PACKAGE%>'/>
</portlet:resourceURL>

<portlet:actionURL var="applyFiltersURL" name="applyFilters">
</portlet:actionURL>

<div class="container" style="display: none;">
	<div class="row">
		<div class="col-3 sidebar">
			<div class="card-deck">
				<div id="searchInput" class="card">
					<div class="card-header">
						<liferay-ui:message key="advanced.search" />
					</div>
                    <div class="card-body">
                        <form action="<%=applyFiltersURL%>" method="post">
                            <div class="form-group">
                                <label for="package_name">Package Name</label>
                                <input type="text" class="form-control form-control-sm" name="<portlet:namespace/><%=Package._Fields.NAME%>"
                                    value="<sw360:out value="${name}"/>" id="package_name">
                            </div>
                            <div class="form-group">
                                <label for="package_version">Package Version</label>
                                <input type="text" class="form-control form-control-sm" name="<portlet:namespace/><%=Package._Fields.NAME%>"
                                    value="<sw360:out value="${version}"/>" id="package_version">
                            </div>
                            <div class="form-group">
                                <label for="package_type">Package Manager Type</label>
                                <select class="form-control form-control-sm" id="package_type" name="<portlet:namespace/><%=Package._Fields.PACKAGE_MANAGER_TYPE%>">
                                    <option value="<%=PortalConstants.NO_FILTER%>" class="textlabel stackedLabel"></option>
                                    <sw360:DisplayEnumOptions type="<%=PackageManagerType.class%>" selectedName="${packageManagerType}" useStringValues="true"/>
                                </select>
                            </div>
                            <div class="form-group">
                                <label for="license_ids">License</label>
                                <input type="text" class="form-control form-control-sm" name="<portlet:namespace/><%=Package._Fields.LICENSE_IDS%>"
                                    value="<sw360:out value="${licenseIds}"/>" id="license_ids">
                            </div>
                            <div class="form-group">
                                <label for="created_by"><liferay-ui:message key="created.by.email" /></label>
                                <input type="text" class="form-control form-control-sm"
                                    name="<portlet:namespace/><%=Package._Fields.CREATED_BY%>"
                                    value="<sw360:out value="${createdBy}"/>" id="created_by">
                            </div>
                            <div class="form-group">
                                <span class="d-flex align-items-center mb-2">
                                    <label class="mb-0 mr-4" for="created_on"><liferay-ui:message key="created.on" /></label>
                                    <select class="form-control form-control-sm w-50" id="dateRange" name="<portlet:namespace/><%=PortalConstants.DATE_RANGE%>">
                                        <option value="<%=PortalConstants.NO_FILTER%>" class="textlabel stackedLabel"></option>
                                        <sw360:DisplayEnumOptions type="<%=DateRange.class%>" selectedName="${dateRange}" useStringValues="true"/>
                                    </select>
                                </span>
                                <input id="created_on" class="datepicker form-control form-control-sm" autocomplete="off"
                                    name="<portlet:namespace/><%=Package._Fields.CREATED_ON%>" <core_rt:if test="${empty createdOn}"> style="display: none;" </core_rt:if>
                                    type="text" pattern="\d{4}-\d{2}-\d{2}" value="<sw360:out value="${createdOn}"/>" />
                                <label id="toLabel" <core_rt:if test="${empty endDate}"> style="display: none;" </core_rt:if> ><liferay-ui:message key="to" /></label>
                                <input type="text" id="endDate" class="datepicker form-control form-control-sm ml-0" autocomplete="off"
                                    name="<portlet:namespace/><%=PortalConstants.END_DATE%>" <core_rt:if test="${empty endDate}"> style="display: none;" </core_rt:if>
                                    value="<sw360:out value="${endDate}"/>" pattern="\d{4}-\d{2}-\d{2}" />
                            </div>
                            <div class="form-group">
                                <input class="form-check-input" type="checkbox" value="On" name="<portlet:namespace/><%=PortalConstants.EXACT_MATCH_CHECKBOX%>"
                                      <core_rt:if test="${exactMatchCheckBox != ''}"> checked="checked"</core_rt:if> />
                                <label class="form-check-label" for="exactMatch"><liferay-ui:message key="exact.match" /></label>
                                <sup title="<liferay-ui:message key="the.search.result.will.display.elements.exactly.matching.the.input.equivalent.to.using.x.around.the.search.keyword" /> <liferay-ui:message key="applied.on.component.name" />">
                                    <liferay-ui:icon icon="info-sign" />
                                </sup>
                            </div>
                            <button type="submit" class="btn btn-primary btn-sm btn-block"><liferay-ui:message key="search" /></button>
				        </form>
					</div>
				</div>
			</div>
		</div>
		<div class="col">
            <div class="row portlet-toolbar">
				<div class="col-auto">
					<div class="btn-toolbar" role="toolbar">
                        <div class="btn-group" role="group">
							<button type="button" class="btn btn-primary" onclick="window.location.href='<%=addPackageURL%>'">Add Package</button>
						</div>
					</div>
				</div>
                <div class="col portlet-title text-truncate" title="<liferay-ui:message key="packages" />">
					<liferay-ui:message key="packages" />
				</div>
            </div>

            <div class="row">
                <div class="col">
			        <table id="packagesTable" class="table table-bordered"></table>
                </div>
            </div>

		</div>
	</div>
</div>
<%@ include file="/html/utils/includes/pageSpinner.jspf" %>

    <div class="dialogs auto-dialogs">
        <div id="deletePackageDialog" class="modal fade" tabindex="-1" role="dialog">
            <div class="modal-dialog modal-lg modal-dialog-centered modal-danger" role="document">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title">
                            <clay:icon symbol="question-circle" />
                            <liferay-ui:message key="delete.package" />?
                        </h5>
                        <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                        </button>
                    </div>
                    <div class="modal-body">
                        <p><liferay-ui:message key="do.you.really.want.to.delete.the.package.x" />?</p>
                        <div data-hide="hasNoDependencies">
                            <p>
                                <liferay-ui:message key="this.release.x.contains.1" />
                            </p>
                            <ul>
                                <li data-hide="hasNoLinkedReleases"><span data-name="linkedReleases"></span><liferay-ui:message key="linked.releases" /> </li>
                                <li data-hide="hasNoAttachments"><span data-name="attachments"></span><liferay-ui:message key="attachments" /> </li>
                            </ul>
                        </div>
                        <hr/>
                        <form>
                            <div class="form-group">
                                <label for="deleteReleaseDialogComment"><liferay-ui:message key="please.comment.your.changes" /></label>
                                <textarea id="deleteReleaseDialogComment" class="form-control" data-name="comment" rows="4" placeholder="<liferay-ui:message key="comment.your.request" />"></textarea>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-light" data-dismiss="modal"><liferay-ui:message key="cancel" /></button>
                        <button type="button" class="btn btn-danger"><liferay-ui:message key="delete.release" /></button>
                    </div>
                </div>
            </div>
        </div>
    </div>
<%--for javascript library loading --%>
<%@ include file="/html/utils/includes/requirejs.jspf" %>
<script>
    AUI().use('liferay-portlet-url', function () {
        var PortletURL = Liferay.PortletURL;

        require(['jquery', 'modules/autocomplete', 'modules/dialog', 'bridges/datatables', 'utils/render' ], function($, autocomplete, dialog, datatables, render) {

            $('.datepicker').datepicker({changeMonth:true,changeYear:true,dateFormat: "yy-mm-dd", maxDate: new Date()}).change(dateChanged).on('changeDate', dateChanged);

            function dateChanged(ev) {
                let id = $(this).attr("id"),
                    dt = $(this).val();
                if (id === "created_on") {
                    $('#endDate').datepicker('option', 'minDate', dt);
                } else if (id === "endDate") {
                    $('#created_on').datepicker('option', 'maxDate', dt ? dt : new Date());
                }
            }

            $('#dateRange').on('change', function (e) {
                let selected = $("#dateRange option:selected").text(),
                    $datePkr = $(".datepicker"),
                    $toLabel = $("#toLabel");

                if (!selected) {
                    $datePkr.hide().val("");
                    $toLabel.hide();
                    return;
                }

                if (selected === "<liferay-ui:message key="between" />" ) {
                    $datePkr.show();
                    $toLabel.show();
                } else {
                    $("#created_on").show();
                    $toLabel.hide();
                    $("#endDate").hide().val("");
                }
            });

                var tableData = [],
                    licenses;
                <core_rt:forEach items="${packageList}" var="pkg">
                    licenses = [];
                    <c:set var = "mainLicenseIds" value = "" />

                    <core_rt:if test="${not empty pkg.licenseIds}">
                    <core_rt:forEach items="${pkg.licenseIds}" var="license">
                        licenses.push("<sw360:out value='${license}'/>");
                    </core_rt:forEach >
                    </core_rt:if>
                    <c:set var = "lic" value = "${licenses}" />
                	tableData.push({
                        "DT_RowId": "${pkg.id}",
                        "0": "",
                        "1": "<sw360:out value='${pkg.name} (${pkg.version})'/>",
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
                    {"title": "<liferay-ui:message key='package.name.with.version' />", render: {display: renderPackageNameLink}, width: "50%"},
                    {"title": "<liferay-ui:message key="licenses" />", render: {display: renderLicenseLink}, width: "20%"},
                    {"title": "<liferay-ui:message key='package.manager' />", width: "15%"},
                    {"title": "<liferay-ui:message key="actions" />", render: {display: renderPackageActions}, className: 'two actions', orderable: false, width: "10%"}
                ];
                let printColumns = [0, 1, 2, 3];
                var packagesTable = datatables.create('#packagesTable', {
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
                }, printColumns);

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
                    .replace(/packages/g, "licenses");// DIRTY WORKAROUND

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

            $('#packagesTable').on('click', 'svg.delete', function(event) {
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
                                $dialog.warning('<liferay-ui:message key="i.could.not.delete.the.package.since.it.is.used.by.another.project" />');
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

            // helper functions
            function makePackageUrl(packageId, page) {
                var portletURL = PortletURL.createURL('<%= PortletURLFactoryUtil.create(request, portletDisplay.getId(), themeDisplay.getPlid(), PortletRequest.RENDER_PHASE) %>')
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
                        'data-package-id': row.DT_RowId,
                        'data-package-name': row[1],
                    });

                $deleteAction.append($('<title>Delete</title><use href="/o/org.eclipse.sw360.liferay-theme/images/clay/icons.svg#trash"/>'));

                $actions.append($editAction, $deleteAction);
                return $actions[0].outerHTML;
            }

            function renderPackageNameLink(data, type, row) {
                return render.linkTo(makePackageUrl(row.DT_RowId, '<%=PortalConstants.PAGENAME_DETAIL%>'), row[1]);
            }

            function replaceFriendlyUrlParameter(portletUrl, id, page) {
                return portletUrl
                    .replace('<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_PAGENAME%>', page)
                    .replace('<%=PortalConstants.FRIENDLY_URL_PLACEHOLDER_ID%>', id);
            }
        });
    });
</script>
<%--
  ~ Copyright Siemens AG, 2013-2016, 2019. Part of the SW360 Portal Project.
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

<div class="jumbotron">
    <core_rt:if test="${not empty welcomePageGuideLine}">
        ${welcomePageGuideLine}
    </core_rt:if>
    <core_rt:if test="${empty welcomePageGuideLine}">
	    <h1 class="display-4"><liferay-ui:message key="welcome.to.sw360" /></h1>
		<%-- Select Language --%>
		<a href="/en_US" title="English"> <img src="<%=request.getContextPath()%>/images/en.png" width="25px" height="25px"></a>
		<a href="/ja_JP" title="Japan"> <img src="<%=request.getContextPath()%>/images/jp.png" width="25px" height="25px"></a>
		<a href="/vi_VN" title="Vietnam"> <img src="<%=request.getContextPath()%>/images/vi.png" width="25px" height="25px"></a>
		<a href="/zh_CN" title="China"> <img src="<%=request.getContextPath()%>/images/cn.png" width="25px" height="25px"></a>
		<br/>
	    <p class="lead">
		<liferay-ui:message key="sw360.is.an.open.source.software.project.that.provides.both.a.web.application.and.a.repository.to.collect.organize.and.make.available.information.about.software.components.it.establishes.a.central.hub.for.software.components.in.an.organization" />
		<p>
            <strong>Additional Resources:</strong>
        </p>
        <ul>
            <li><a href="https://teams.microsoft.com/l/team/19%3aUjBY3R8YTaI3Wik7-LKOA6Zpzk-j1ZIreeQ9Aa0MNQE1%40thread.tacv2/conversations?groupId=eec48a5b-4288-4f46-8c62-4f108410a641&tenantId=5dbf1add-202a-4b8d-815b-bf0fb024e033" target = "_blank">SHS SW360 User Group on MS Teams</a>&nbsp;</li>
            <li><a href="https://code.siemens.com/SHS/shs-sw-360-portal/issues" target = "_blank"> Healthineers Issue Tracker</a></li>
            <li><a href="/resource/docs/api-guide.html" target = "_blank"> REST API Docs</a></li>
            <li><a href="https://www.eclipse.org/sw360/docs/userguide/" target="_blank"> SW360 Docs</a></li>
            <li><a href="http://www.github.com/eclipse/sw360" target="_blank"> SW360</a></li>
        </ul>
	    </p>
    </core_rt:if>
	<hr class="my-4">
	<core_rt:if test="${themeDisplay.signedIn}">
		<h3><liferay-ui:message key="you.are.signed.in.please.go.ahead.using.sw360" /></h3>
		<div class="buttons">
			<a class="btn btn-primary btn-lg" href="/group/guest/home" role="button"><liferay-ui:message key="start" /></a>
		</div>
	</core_rt:if>
	<core_rt:if test="${not themeDisplay.signedIn}">
		<h3><liferay-ui:message key="in.order.to.go.ahead.please.sign.in.or.create.a.new.account" /></h3>
		<div class="buttons">
			<span class="sign-in"><a class="btn btn-primary btn-lg" href="${ themeDisplay.getURLSignIn() }" role="button"><liferay-ui:message key="sign.in" /></a></span>
			<a class="btn btn-outline-primary btn-lg" href="/web/guest/sign-up" role="button"><liferay-ui:message key="create.account" /></a>
		</div>
	</core_rt:if>
</div>
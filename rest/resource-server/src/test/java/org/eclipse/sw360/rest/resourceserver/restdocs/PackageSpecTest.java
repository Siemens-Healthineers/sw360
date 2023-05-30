/*
 * Copyright Siemens Healthineers GmBH, 2023. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.sw360.rest.resourceserver.restdocs;

import com.google.common.collect.ImmutableSet;
import org.apache.thrift.TException;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.attachments.Attachment;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentContent;
import org.eclipse.sw360.datahandler.thrift.attachments.AttachmentType;
import org.eclipse.sw360.datahandler.thrift.attachments.CheckStatus;
import org.eclipse.sw360.datahandler.thrift.components.Component;
import org.eclipse.sw360.datahandler.thrift.components.ComponentType;
import org.eclipse.sw360.datahandler.thrift.components.Release;
import org.eclipse.sw360.datahandler.thrift.projects.Project;
import org.eclipse.sw360.datahandler.thrift.projects.ProjectType;
import org.eclipse.sw360.datahandler.thrift.packages.Package;
import org.eclipse.sw360.datahandler.thrift.packages.PackageManagerType;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.rest.resourceserver.TestHelper;
import org.eclipse.sw360.rest.resourceserver.packages.SW360PackageService;
import org.eclipse.sw360.rest.resourceserver.release.Sw360ReleaseService;
import org.eclipse.sw360.rest.resourceserver.user.Sw360UserService;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.CollectionModel;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.linkWithRel;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.links;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
public class PackageSpecTest extends TestRestDocsSpecBase {

    @Value("${sw360.test-user-id}")
    private String testUserId;

    @Value("${sw360.test-user-password}")
    private String testUserPassword;

    @MockBean
    private Sw360UserService userServiceMock;

    @MockBean
    private SW360PackageService packageServiceMock;

    @MockBean
    private Sw360ReleaseService releaseServiceMock;

    private Package package1;
    private Package package2;
    private Set<String> licenseIds;

    @Before
    public void before() throws TException, IOException {
        Set<Attachment> setOfAttachment = new HashSet<Attachment>();
        Attachment att1 = new Attachment("1234", "test.zip").setAttachmentType(AttachmentType.SOURCE)
                .setCreatedBy("user@sw360.org").setSha1("da373e491d312365483589ee9457bc316783").setCreatedOn("2021-04-27")
                .setCreatedTeam("DEPARTMENT");
        Attachment att2 = att1.deepCopy().setAttachmentType(AttachmentType.BINARY).setCreatedComment("Created Comment")
                .setCheckStatus(CheckStatus.ACCEPTED).setCheckedComment("Checked Comment").setCheckedOn("2021-04-27")
                .setCheckedBy("admin@sw360.org").setCheckedTeam("DEPARTMENT1");

        setOfAttachment.add(att1);
        setOfAttachment.add(att2);

        Release testRelease = new Release().setAttachments(setOfAttachment).setId("98745").setName("Test Release")
                .setVersion("2").setComponentId("17653524").setCreatedOn("2021-04-27").setCreatedBy("admin@sw360.org");

        given(this.releaseServiceMock.getReleaseForUserById(eq(testRelease.getId()), any())).willReturn(testRelease);

        licenseIds = new HashSet<>();
        licenseIds.add("MIT");
        licenseIds.add("GPL");

        package1 = new Package("angular-sanitize","1.8.2","pkg:npm/angular-sanitize@1.8.2")
                        .setId("122357345")
                        .setCreatedBy("admin@sw360.org")
                        .setCreatedOn("2023-01-02")
                        .setVcs("git+https://github.com/angular/angular.js.git")
                        .setHomepageUrl("http://angularjs.org")
                        .setLicenseIds(licenseIds)
                        .setReleaseId(testRelease.getId())
                        .setPackageManagerType(PackageManagerType.NPM)
                        .setDescription("Sanitizes an html string by stripping all potentially dangerous tokens.");

        package2 = new Package()
                        .setId("875689754")
                        .setName("applicationinsights-web")
                        .setVersion("2.5.11")
                        .setCreatedBy("user@sw360.org")
                        .setCreatedOn("2023-02-02")
                        .setPurl("pkg:npm/@microsoft/applicationinsights-web@2.5.11")
                        .setPackageManagerType(PackageManagerType.NPM)
                        .setVcs("git+https://github.com/microsoft/ApplicationInsights-JS.git")
                        .setHomepageUrl("https://github.com/microsoft/ApplicationInsights-JS#readme")
                        .setDescription("Application Insights is an extension of Azure Monitor and provides application performance monitoring (APM) features");

        when(this.packageServiceMock.createPackage(any(), any())).then(invocation ->
        new Package(package1));

        given(this.packageServiceMock.getPackageForUserById(eq(package1.getId()))).willReturn(package1);
        given(this.packageServiceMock.getPackageForUserById(eq(package2.getId()))).willReturn(package2);
        given(this.packageServiceMock.deletePackage(eq(package1.getId()), any())).willReturn(RequestStatus.SUCCESS);

        given(this.userServiceMock.getUserByEmailOrExternalId("admin@sw360.org")).willReturn(
                new User("admin@sw360.org", "sw360").setId("123456789"));
        given(this.userServiceMock.getUserByEmail("admin@sw360.org")).willReturn(
                new User("admin@sw360.org", "sw360").setId("123456789"));
        given(this.userServiceMock.getUserByEmailOrExternalId("user@sw360.org")).willReturn(
                new User("user@sw360.org", "sw360").setId("12345670089"));
        given(this.userServiceMock.getUserByEmail("user@sw360.org")).willReturn(
                new User("user@sw360.org", "sw360").setId("12345670089"));
    }

    @Test
    public void should_document_create_package() throws Exception {
        Map<String, Object> pkg = new LinkedHashMap<>();

        pkg.put("name", "angular-sanitize");
        pkg.put("version", "1.8.2");
        pkg.put("purl", "pkg:npm/angular-sanitize@1.8.2");
        pkg.put("vcs", "git+https://github.com/angular/angular.js.git");
        pkg.put("homepageUrl", "https://github.com/angular/angular-sanitize");
        pkg.put("licenseIds", licenseIds);
        pkg.put("releaseId", "69da0e106074ab7b3856df5fbc3e");
        pkg.put("description", "Sanitizes an html string by stripping all potentially dangerous tokens.");

        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        this.mockMvc.perform(
                post("/api/packages")
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper.writeValueAsString(pkg))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("_embedded.createdBy.email", Matchers.is("admin@sw360.org")))
                .andDo(this.documentationHandler.document(
                        requestFields(
                                fieldWithPath("name").description("The name of the package"),
                                fieldWithPath("version").description("The version of the package"),
                                fieldWithPath("purl").description("Package URL"),
                                fieldWithPath("vcs").description("VCS(Version Control System) is the URL of the source code"),
                                fieldWithPath("homepageUrl").description("URL of the package website"),
                                fieldWithPath("licenseIds").description("The associated licenses"),
                                fieldWithPath("releaseId").description("Id of the linked release"),
                                fieldWithPath("description").description("Description of the package")
                        ),
                        responseFields(
                                fieldWithPath("name").description("The name of the package"),
                                fieldWithPath("version").description("The version of the package"),
                                fieldWithPath("createdOn").description("The date of creation of the package"),
                                fieldWithPath("packageManagerType").description("The type of package manager"),
                                fieldWithPath("purl").description("Package URL"),
                                fieldWithPath("vcs").description("VCS(Version Control System) is the URL of the source code"),
                                fieldWithPath("homepageUrl").description("URL of the package website"),
                                fieldWithPath("licenseIds").description("The associated licenses"),
                                fieldWithPath("description").description("Description of the package"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                subsectionWithPath("_embedded.sw360:releases").description("The release to which the package is linked"),
                                subsectionWithPath("_embedded.createdBy").description("The user who created this component")
                        )));
    }

    @Test
    public void should_document_update_package() throws Exception {
        Package updatePackage = new Package()
                                    .setHomepageUrl("https://angularJS.org")
                                    .setDescription("Updated Description");
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(patch("/api/packages/" + package1.getId())
                .contentType(MediaTypes.HAL_JSON)
                .content(this.objectMapper.writeValueAsString(updatePackage))
                .header("Authorization", "Bearer" + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        requestFields(
                                fieldWithPath("homepageUrl").description("URL of the package website"),
                                fieldWithPath("description").description("Description of the package")
                        ),
                        responseFields(
                                fieldWithPath("name").description("The name of the package"),
                                fieldWithPath("version").description("The version of the package"),
                                fieldWithPath("createdOn").description("The date of creation of the package"),
                                fieldWithPath("packageManagerType").description("The type of package manager"),
                                fieldWithPath("purl").description("Package URL"),
                                fieldWithPath("vcs").description("VCS(Version Control System) is the URL of the source code"),
                                fieldWithPath("homepageUrl").description("URL of the package website"),
                                fieldWithPath("licenseIds").description("The associated licenses"),
                                fieldWithPath("description").description("Description of the package"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                subsectionWithPath("_embedded.sw360:releases").description("The release to which the package is linked"),
                                subsectionWithPath("_embedded.createdBy").description("The user who created this component"))
                        ));
    }

    @Test
    public void should_document_delete_package() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(delete("/api/packages/" + package1.getId())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void should_document_get_package() throws Exception {
        String accessToken = TestHelper.getAccessToken(mockMvc, testUserId, testUserPassword);
        mockMvc.perform(get("/api/packages/" + package1.getId())
                .header("Authorization", "Bearer " + accessToken)
                .accept(MediaTypes.HAL_JSON))
                .andExpect(status().isOk())
                .andDo(this.documentationHandler.document(
                        links(
                                linkWithRel("self").description("The <<resources-packages,Packages resource>>")
                        ),
                        responseFields(
                                fieldWithPath("name").description("The name of the package"),
                                fieldWithPath("version").description("The version of the package"),
                                fieldWithPath("createdOn").description("The date of creation of the package"),
                                fieldWithPath("packageManagerType").description("The type of package manager"),
                                fieldWithPath("purl").description("Package URL"),
                                fieldWithPath("vcs").description("VCS(Version Control System) is the URL of the source code"),
                                fieldWithPath("homepageUrl").description("URL of the package website"),
                                fieldWithPath("licenseIds").description("The associated licenses"),
                                fieldWithPath("description").description("Description of the package"),
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"),
                                subsectionWithPath("_embedded.sw360:releases").description("The release to which the package is linked"),
                                subsectionWithPath("_embedded.createdBy").description("The user who created this component")
                        )));


    }
}
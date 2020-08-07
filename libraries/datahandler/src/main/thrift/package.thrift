/*
 * Copyright Siemens Healthineers GmBH, 2023. Part of the SW360 Portal Project.
 * With contributions by Bosch Software Innovations GmbH, 2016.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
include "sw360.thrift"
include "components.thrift"
include "users.thrift"
include "vendors.thrift"

namespace java org.eclipse.sw360.datahandler.thrift.packages
namespace php sw360.thrift.packages

typedef sw360.AddDocumentRequestSummary AddDocumentRequestSummary
typedef sw360.RequestSummary RequestSummary
typedef sw360.RequestStatus RequestStatus
typedef sw360.SW360Exception SW360Exception
typedef sw360.PaginationData PaginationData
typedef users.User User
typedef vendors.Vendor Vendor
typedef components.Release Release

struct Package {
    1: optional string id,
    2: optional string revision,
    3: optional string type = "package",
    4: required string name,
    5: required string version,
    6: required string purl, // package url
    7: optional string releaseId,
    8: optional set<string> licenseIds,
    9: optional string description,
    10: optional string homepageUrl,
    11: optional string hash,
    13: optional string vcs, // version control system
    14: optional string createdOn,
    15: optional string createdBy,
    16: optional string modifiedOn,
    17: optional string modifiedBy,
    18: optional PackageManagerType packageManagerType,
    19: optional Vendor vendor, // Supplier or Manufacturer
    20: optional string vendorId,
    30: optional Release release // only used for Linked packages UI in project details page.
}

enum PackageManagerType {
    ALPINE = 0,
    ALPM = 1,
    APK = 2,
    BITBUCKET = 3,
    CARGO = 4,
    COMPOSER = 5,
    CONAN = 6,
    CONDA = 7,
    CPAN = 8,
    DEB = 9,
    DOCKER = 10,
    GEM = 11,
    GENERIC = 12,
    GITHUB = 13,
    GOLANG = 14,
    GRADLE = 15,
    MAVEN = 16,
    NUGET = 17,
    NPM = 18,
    PYPI = 19,
    RPM = 20,
    YARN = 21,
    YOCTO = 22
}

service PackageService {
    /**
     * Get Package by Id
     */
    Package getPackageById(1: string packageId) throws (1: SW360Exception exp);

    /**
     * Get Packages by list of Package id
     */
    list<Package> getPackageByIds(1: set<string> packageIds) throws (1: SW360Exception exp);

    /**
     * Get All Packages for package portal landing page and empty search
     */
    list<Package> getAllPackages();

    /**
     * Get All Orphan Packages for search / link to release
     */
    list<Package> getAllOrphanPackages();

    /**
     * global search function to list packages which match the text argument
     */
    list<Package> searchPackages(1: string text, 2: User user);

    /**
     * search packages in database that match subQueryRestrictions
     */
    list<Package> searchPackagesWithFilter(1: string text, 2: map<string, set<string>> subQueryRestrictions);

    /**
     * global search function to list orphan packages which match the text argument
     */
    list<Package> searchOrphanPackages(1: string text, 2: User user);

    /**
     * Get list of all the Package by list of release id
     */
    set<Package> getPackagesByReleaseIds(1: set<string> releaseIds) throws (1: SW360Exception exp);

    /**
     * Get list of all the Package by release id
     */
    set<Package> getPackagesByReleaseId(1: string releaseId) throws (1: SW360Exception exp);

    /**
     * Add package to database with user as creator, return package id
     */
    AddDocumentRequestSummary addPackage(1: Package pkg, 2: User user) throws (1: SW360Exception exp);

    /**
     * Update Package
     */
    RequestStatus updatePackage(1: Package pkg, 2: User user) throws (1: SW360Exception exp);

    /**
     * Try to update a package as a user.
     * If forceUpdate is true, this function can update regardless of write permissions.
     */
    RequestStatus updatePackageWithForceFlag(1: Package pkg, 2: User user, 3: bool forceUpdate);

    /**
     * Delete Package by Id
     */
    RequestStatus deletePackage(1: string pacakgeId, 2: User user) throws (1: SW360Exception exp);

    /**
     * Try to delete a package as a user.
     * If forceDelete is true, this function can delete regardless of delete permissions.
     */
    RequestStatus deletePackageWithForceFlag(1: string id, 2: User user, 3: bool forceDelete);

    /**
     * Parse a CycloneDx SBoM file (XML or JSON) and write the information to SW360 as Project / Component / Release / Package
     */
    RequestSummary importCycloneDxFromAttachmentContent(1: User user, 2: string attachmentContentId, 3: string projectId);

    /**
     * Details of all packages with pagination
     **/
    map<PaginationData, list<Package>> getPackagesWithPagination(1: PaginationData pageData);

    /**
     * total number of packages in the DB
     **/
    i32 getTotalPackagesCount();

}

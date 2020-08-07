/*
 * Copyright Siemens Healthineers GmBH, 2023. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.db;

import static com.google.common.base.Strings.isNullOrEmpty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.sw360.datahandler.cloudantclient.DatabaseConnectorCloudant;
import org.eclipse.sw360.datahandler.cloudantclient.DatabaseRepositoryCloudantClient;
import org.eclipse.sw360.datahandler.thrift.packages.Package;
import org.eclipse.sw360.datahandler.thrift.users.User;

import com.cloudant.client.api.model.DesignDocument.MapReduce;

/**
 * CRUD access for the Package class
 *
 * @author abdul.kapti@siemens-healthineers.com
 */

public class PackageRepository extends DatabaseRepositoryCloudantClient<Package> {

    private static final String ALL = "function(doc) { if (doc.type == 'package') emit(null, doc._id) }";
    private static final String ORPHAN = "function(doc) { if (doc.type == 'package' && !doc.releaseId) emit(null, doc._id) }";
    private static final String BY_NAME = "function(doc) { if (doc.type == 'package') { emit(doc.name.trim(), doc._id) } }";
    private static final String BY_NAME_LOWERCASE = "function(doc) { if (doc.type == 'package') { emit(doc.name.toLowerCase().trim(), doc._id) } }";
    private static final String BY_VESION_LOWERCASE = "function(doc) { if (doc.type == 'package') { emit(doc.version.toLowerCase().trim(), doc._id) } }";
    private static final String BY_PKG_MANAGER_TYPE = "function(doc) { if (doc.type == 'package') { emit(doc.packageManagerType.toLowerCase().trim(), doc._id) } }";
    private static final String BY_CREATOR = "function(doc) { if (doc.type == 'package') { emit(doc.createdBy, doc._id) } }";
    private static final String BY_CREATED_ON = "function(doc) { if (doc.type == 'package') { emit(doc.createdOn, doc._id) } }";
    private static final String BY_RELEASE_ID = "function(doc) { if (doc.type == 'package') { emit(doc.releaseId, doc._id); } }";
    private static final String BY_LICENSE_ID = "function(doc) { if (doc.type == 'pavkage') { if (doc.licenseIds) { emit(doc.licenseIds.join(), doc._id); } else { emit('', doc._id); } } }";

    public PackageRepository(DatabaseConnectorCloudant db) {
        super(db, Package.class);
        Map<String, MapReduce> views = new HashMap<String, MapReduce>();
        views.put("all", createMapReduce(ALL, null));
        views.put("orphan", createMapReduce(ORPHAN, null));
        views.put("byName", createMapReduce(BY_NAME, null));
        views.put("byNameLowerCase", createMapReduce(BY_NAME_LOWERCASE, null));
        views.put("byVersionLowerCase", createMapReduce(BY_VESION_LOWERCASE, null));
        views.put("byPackageManagerType", createMapReduce(BY_PKG_MANAGER_TYPE, null));
        views.put("byCreator", createMapReduce(BY_CREATOR, null));
        views.put("byCreatedOn", createMapReduce(BY_CREATED_ON, null));
        views.put("byReleaseId", createMapReduce(BY_RELEASE_ID, null));
        views.put("byLicenseId", createMapReduce(BY_LICENSE_ID, null));
        initStandardDesignDocument(views, db);
    }

    public List<Package> getOrphanPackage() {
        return queryView("orphan");
    }

    public List<Package> getPackagesByReleaseId(String id) {
        return queryView("byReleaseId", id);
    }

    public List<Package> searchByName(String name, User user) {
        return queryView("byName", name);
    }

    public List<Package> searchByNameLowerCase(String name, User user) {
        return queryView("byNameLowerCase", name.toLowerCase());
    }

    public List<Package> searchByVersionLowerCase(String version, User user) {
        return queryView("byVersionLowerCase", version.toLowerCase());
    }

    public List<Package> searchByPackageManagerType(String pkgType, User user) {
        return queryView("byPackageManagerType", pkgType.toUpperCase());
    }

    public List<Package> searchByCreator(String email, User user) {
        return queryView("byCreator", email);
    }

    public List<Package> searchByLicenseeId(String id) {
        return queryView("byLicenseId", id);
    }

    public List<Package> searchByNameAndVersion(String name, String version, boolean caseInsenstive) {
        List<Package> releasesMatchingName;
        if (caseInsenstive) {
            releasesMatchingName = new ArrayList<Package>(queryView("byNameLowerCase", name));
        } else {
            releasesMatchingName = new ArrayList<Package>(queryView("byname", name));
        }
        List<Package> releasesMatchingNameAndVersion = releasesMatchingName.stream()
                .filter(r -> isNullOrEmpty(version) ? isNullOrEmpty(r.getVersion()) : version.equals(r.getVersion()))
                .collect(Collectors.toList());
        return releasesMatchingNameAndVersion;
    }

}

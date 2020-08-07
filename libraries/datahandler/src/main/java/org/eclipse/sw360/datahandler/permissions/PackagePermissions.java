/*
 * Copyright Siemens Healthineers GmBH, 2023. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.datahandler.permissions;


import java.util.Map;
import java.util.Set;

import org.eclipse.sw360.datahandler.thrift.packages.Package;
import org.eclipse.sw360.datahandler.thrift.users.RequestedAction;
import org.eclipse.sw360.datahandler.thrift.users.User;

import com.google.common.collect.Sets;

public class PackagePermissions extends DocumentPermissions<Package> {
    private final Set<String> moderators;
    private final Set<String> contributors;

    protected PackagePermissions(Package document, User user) {
        super(document, user);

        moderators = Sets.newHashSet(document.createdBy);
        contributors = moderators;

    }

    @Override
    public void fillPermissions(Package other, Map<RequestedAction, Boolean> permissions) {
    }

    @Override
    public boolean isActionAllowed(RequestedAction action) {
        return getStandardPermissions(action);
    }

    @Override
    protected Set<String> getContributors() {
        return contributors;
    }

    @Override
    protected Set<String> getModerators() {
        return moderators;
    }
}

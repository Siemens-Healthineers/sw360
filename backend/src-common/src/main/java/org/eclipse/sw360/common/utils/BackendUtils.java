/*
 * Copyright Siemens AG, 2019. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.common.utils;

import java.util.Properties;

import org.eclipse.sw360.datahandler.common.CommonUtils;

public class BackendUtils {

    private static final String PROPERTIES_FILE_PATH = "/sw360.properties";
    protected static final Properties loadedProperties;
    public static final Boolean MAINLINE_STATE_ENABLED_FOR_USER;
    public static final Boolean IS_FORCE_UPDATE_ENABLED;
    public static final boolean IS_PACKAGE_PORTLET_DISABLED;

    static {
        loadedProperties = CommonUtils.loadProperties(BackendUtils.class, PROPERTIES_FILE_PATH);
        MAINLINE_STATE_ENABLED_FOR_USER = Boolean.parseBoolean(loadedProperties.getProperty("mainline.state.enabled.for.user", "false"));
        IS_FORCE_UPDATE_ENABLED = Boolean.parseBoolean(
                System.getProperty("RunRestForceUpdateTest", loadedProperties.getProperty("rest.force.update.enabled", "false")));
        IS_PACKAGE_PORTLET_DISABLED = Boolean.parseBoolean(loadedProperties.getProperty("package.portlet.disabled", "true"));
    }

    protected BackendUtils() {
        // Utility class with only static functions
    }
}

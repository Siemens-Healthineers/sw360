/*
 * Copyright Siemens AG, 2019. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.portal.tags;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.SimpleTagSupport;

import com.google.common.base.Strings;

import org.apache.commons.lang.StringEscapeUtils;

import java.io.IOException;

public class Icon extends SimpleTagSupport {

    private String icon = "";
    private String title = "";
    private String className = "";

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void doTag() throws JspException, IOException {
        String tag = "<svg class=\"lexicon-icon " + className + "\">";

        if(!Strings.isNullOrEmpty(title)) {
            tag += "<title>" + StringEscapeUtils.escapeHtml(title) + "</title>";
        }
        tag += "<use href=\"" + ((PageContext) getJspContext()).getServletContext().getContextPath() + "/images/icons.svg#" + icon + "\"/>";

        tag += "</svg>";
        getJspContext().getOut().print(tag);
    }
}

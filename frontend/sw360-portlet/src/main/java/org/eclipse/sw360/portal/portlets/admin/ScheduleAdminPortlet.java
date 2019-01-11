/*
 * Copyright Siemens AG, 2013-2016. Part of the SW360 Portal Project.
 * With contributions by Bosch Software Innovations GmbH, 2016.
 *
 * SPDX-License-Identifier: EPL-1.0
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.sw360.portal.portlets.admin;

import org.eclipse.sw360.datahandler.common.CommonUtils;
import org.eclipse.sw360.datahandler.thrift.*;
import org.eclipse.sw360.datahandler.thrift.schedule.ScheduleService;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.portal.common.PortalConstants;
import org.eclipse.sw360.portal.common.UsedAsLiferayAction;
import org.eclipse.sw360.portal.portlets.Sw360Portlet;
import org.eclipse.sw360.portal.users.UserCacheHolder;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;

import javax.portlet.*;
import java.io.IOException;

public class ScheduleAdminPortlet extends Sw360Portlet {

    private static final Logger log = Logger.getLogger(ScheduleAdminPortlet.class);


    @Override
    public void doView(RenderRequest request, RenderResponse response) throws IOException, PortletException {
        prepareStandardView(request, response);
        super.doView(request, response);
    }

    private void prepareStandardView(RenderRequest request, RenderResponse response) {
        try {
            User user = UserCacheHolder.getUserFromRequest(request);
            ScheduleService.Iface scheduleClient = new ThriftClients().makeScheduleClient();

            boolean isAnyServiceScheduled = isAnyServiceScheduled(scheduleClient, user);
            request.setAttribute(PortalConstants.ANY_SERVICE_IS_SCHEDULED, isAnyServiceScheduled);

            putServiceAttributesInRequest(request, user, scheduleClient, ThriftClients.CVESEARCH_SERVICE, PortalConstants.CVESEARCH_IS_SCHEDULED, PortalConstants.CVESEARCH_OFFSET, PortalConstants.CVESEARCH_INTERVAL, PortalConstants.CVESEARCH_NEXT_SYNC);
            putServiceAttributesInRequest(request, user, scheduleClient, ThriftClients.SVMSYNC_SERVICE, PortalConstants.SVMSYNC_IS_SCHEDULED, PortalConstants.SVMSYNC_OFFSET, PortalConstants.SVMSYNC_INTERVAL, PortalConstants.SVMSYNC_NEXT_SYNC);
            putServiceAttributesInRequest(request, user, scheduleClient, ThriftClients.SVMMATCH_SERVICE, PortalConstants.SVMMATCH_IS_SCHEDULED, PortalConstants.SVMMATCH_OFFSET, PortalConstants.SVMMATCH_INTERVAL, PortalConstants.SVMMATCH_NEXT_SYNC);
            putServiceAttributesInRequest(request, user, scheduleClient, ThriftClients.SVM_LIST_UPDATE_SERVICE, PortalConstants.SVM_LIST_UPDATE_IS_SCHEDULED, PortalConstants.SVM_LIST_UPDATE_OFFSET, PortalConstants.SVM_LIST_UPDATE_INTERVAL, PortalConstants.SVM_LIST_UPDATE_NEXT_SYNC);
            putServiceAttributesInRequest(request, user, scheduleClient, ThriftClients.SVM_TRACKING_FEEDBACK_SERVICE, PortalConstants.SVM_TRACKING_FEEDBACK_IS_SCHEDULED, PortalConstants.SVM_TRACKING_FEEDBACK_OFFSET, PortalConstants.SVM_TRACKING_FEEDBACK_INTERVAL, PortalConstants.SVM_TRACKING_FEEDBACK_NEXT_SYNC);
        } catch (TException te) {
            log.error(te.getMessage());
        }

    }

    private void putServiceAttributesInRequest(RenderRequest request, User user, ScheduleService.Iface scheduleClient, String serviceName, String isScheduledBeanName, String offsetBeanName, String intervalBeanName, String nextSyncBeanName) throws TException {
        boolean isCveSearchScheduled = isServiceScheduled(serviceName, scheduleClient, user);
        request.setAttribute(isScheduledBeanName, isCveSearchScheduled);
        int offsetInSeconds = scheduleClient.getFirstRunOffset(serviceName);
        request.setAttribute(offsetBeanName, CommonUtils.formatTime(offsetInSeconds));
        int intervalInSeconds = scheduleClient.getInterval(serviceName);
        request.setAttribute(intervalBeanName, CommonUtils.formatTime(intervalInSeconds));
        String nextSync = scheduleClient.getNextSync(serviceName);
        request.setAttribute(nextSyncBeanName, nextSync);
    }

    private boolean isServiceScheduled(String serviceName, ScheduleService.Iface scheduleClient, User user) throws TException{
            RequestStatusWithBoolean requestStatus = scheduleClient.isServiceScheduled(serviceName, user);
            if(RequestStatus.SUCCESS.equals(requestStatus.getRequestStatus())){
                return requestStatus.isAnswerPositive();
            } else {
                throw new SW360Exception("Backend query for schedule status of cvesearch failed.");
            }
    }

    private boolean isAnyServiceScheduled(ScheduleService.Iface scheduleClient, User user) throws TException{
        RequestStatusWithBoolean requestStatus = scheduleClient.isAnyServiceScheduled(user);
        if(RequestStatus.SUCCESS.equals(requestStatus.getRequestStatus())){
            return requestStatus.isAnswerPositive();
        } else {
            throw new SW360Exception("Backend query for schedule status of services failed.");
        }
    }

    @UsedAsLiferayAction
    public void scheduleCveSearch(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        scheduleService(ThriftClients.CVESEARCH_SERVICE, request);
    }

    @UsedAsLiferayAction
    public void unscheduleCveSearch(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        unscheduleService(ThriftClients.CVESEARCH_SERVICE, request);
    }

    @UsedAsLiferayAction
    public void scheduleSvmSync(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        scheduleService(ThriftClients.SVMSYNC_SERVICE, request);
    }

    @UsedAsLiferayAction
    public void unscheduleSvmSync(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        unscheduleService(ThriftClients.SVMSYNC_SERVICE, request);
    }

    @UsedAsLiferayAction
    public void triggerSvmSync(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        try {
            RequestStatus requestStatus = new ThriftClients().makeVMClient().synchronizeComponents().getRequestStatus();
            setSessionMessage(request, requestStatus, "Task", "perform");
        } catch (TException e) {
            log.error(e);
        }
    }

    @UsedAsLiferayAction
    public void scheduleSvmMatch(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        scheduleService(ThriftClients.SVMMATCH_SERVICE, request);
    }

    @UsedAsLiferayAction
    public void unscheduleSvmMatch(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        unscheduleService(ThriftClients.SVMMATCH_SERVICE, request);
    }

    @UsedAsLiferayAction
    public void triggerSvmMatch(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        try {
            RequestStatus requestStatus = new ThriftClients().makeVMClient().triggerReverseMatch().getRequestStatus();
            setSessionMessage(request, requestStatus, "Task", "perform");
        } catch (TException e) {
            log.error(e);
        }
    }

    @UsedAsLiferayAction
    public void scheduleSvmListUpdate(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        scheduleService(ThriftClients.SVM_LIST_UPDATE_SERVICE, request);
    }

    /**
     * This action is for manually triggering export to SVM for monitoring lists (as opposed to scheduled execution)
     * The action is performed _synchronously_!
     *
     * @param request
     * @param response
     * @throws PortletException
     * @throws IOException
     */
    @UsedAsLiferayAction
    public void triggerSvmListUpdate(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        try {
            RequestStatus requestStatus = new ThriftClients().makeProjectClient().exportForMonitoringList();
            setSessionMessage(request, requestStatus, "Task", "perform");
        } catch (TException e) {
            log.error(e);
        }
    }

    @UsedAsLiferayAction
    public void unscheduleSvmListUpdate(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        unscheduleService(ThriftClients.SVM_LIST_UPDATE_SERVICE, request);
    }

    @UsedAsLiferayAction
    public void scheduleTrackingFeedback(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        scheduleService(ThriftClients.SVM_TRACKING_FEEDBACK_SERVICE, request);
    }

    /**
     * This action is for manually triggering fetching feedback form SVM about tracked releases (as opposed to scheduled execution)
     * The action is performed _synchronously_!
     *
     * @param request
     * @param response
     * @throws PortletException
     * @throws IOException
     */
    @UsedAsLiferayAction
    public void triggerTrackingFeedback(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        try {
            RequestStatus requestStatus = new ThriftClients().makeComponentClient().updateReleasesWithSvmTrackingFeedback();
            setSessionMessage(request, requestStatus, "Task", "perform");
        } catch (TException e) {
            log.error(e);
        }
    }

    @UsedAsLiferayAction
    public void unscheduleTrackingFeedback(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        unscheduleService(ThriftClients.SVM_TRACKING_FEEDBACK_SERVICE, request);
    }

    @UsedAsLiferayAction
    public void unscheduleAllServices(ActionRequest request, ActionResponse response) throws PortletException, IOException {
        try {
            User user = UserCacheHolder.getUserFromRequest(request);
            RequestStatus requestStatus =
                    new ThriftClients().makeScheduleClient().unscheduleAllServices(user);
            setSessionMessage(request, requestStatus, "Every task", "unschedule");
        } catch (TException e) {
            log.error(e);
        }
    }

    private void scheduleService(String serviceName, ActionRequest request) throws PortletException {
        try {
            User user = UserCacheHolder.getUserFromRequest(request);
            RequestSummary requestSummary =
                    new ThriftClients().makeScheduleClient().scheduleService(serviceName);
            setSessionMessage(request, requestSummary.getRequestStatus(), "Task", "schedule");
        } catch (TException e) {
            log.error(e);
        }
    }

    private void unscheduleService(String serviceName, ActionRequest request) throws PortletException {
        try {
            User user = UserCacheHolder.getUserFromRequest(request);
            RequestStatus requestStatus =
                    new ThriftClients().makeScheduleClient().unscheduleService(serviceName, user);
            setSessionMessage(request, requestStatus, "Task", "unschedule");
        } catch (TException e) {
            log.error(e);
        }
    }
}

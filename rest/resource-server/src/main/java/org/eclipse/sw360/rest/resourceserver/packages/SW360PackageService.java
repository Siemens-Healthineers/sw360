/*
 * Copyright Siemens Healthineers GmBH, 2023. Part of the SW360 Portal Project.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.sw360.rest.resourceserver.packages;

import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;
import org.eclipse.sw360.datahandler.common.SW360Utils;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestStatus;
import org.eclipse.sw360.datahandler.thrift.AddDocumentRequestSummary;
import org.eclipse.sw360.datahandler.thrift.RequestStatus;
import org.eclipse.sw360.datahandler.thrift.SW360Exception;
import org.eclipse.sw360.datahandler.thrift.ThriftClients;
import org.eclipse.sw360.datahandler.thrift.packages.Package;
import org.eclipse.sw360.datahandler.thrift.packages.PackageService;
import org.eclipse.sw360.datahandler.thrift.users.User;
import org.eclipse.sw360.rest.resourceserver.Sw360ResourceServer;
import org.eclipse.sw360.rest.resourceserver.core.RestControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class SW360PackageService {
    @Value("${sw360.thrift-server-url:http://localhost:8080}")
    private String thriftServerUrl;

    @NonNull
    private final RestControllerHelper<Package> rch;

    public Package createPackage(Package pkg, User sw360User) throws TException {
        PackageService.Iface sw360PackageClient = getThriftPackageClient();
        AddDocumentRequestSummary documentRequestSummary = sw360PackageClient.addPackage(pkg, sw360User);
        if (documentRequestSummary.getRequestStatus() == AddDocumentRequestStatus.SUCCESS) {
            pkg.setId(documentRequestSummary.getId());
            pkg.setCreatedBy(sw360User.getEmail());
            return pkg;
        } else if (documentRequestSummary.getRequestStatus() == AddDocumentRequestStatus.DUPLICATE) {
            throw new DataIntegrityViolationException("sw360 package with name '" + pkg.getName() + "' already exists.");
        } else if (documentRequestSummary.getRequestStatus() == AddDocumentRequestStatus.INVALID_INPUT) {
            throw new HttpMessageNotReadableException("Dependent document Id/ids not valid.");
        } else if (documentRequestSummary.getRequestStatus() == AddDocumentRequestStatus.NAMINGERROR) {
            throw new HttpMessageNotReadableException("Package name field cannot be empty or contain only whitespace character");
        }
        return null;
    }

    public RequestStatus updatePackage(Package pkg, User sw360User) throws TException {
        PackageService.Iface sw360PackageClient = getThriftPackageClient();
        rch.checkForCyclicOrInvalidDependencies(sw360PackageClient, pkg, sw360User);

        RequestStatus requestStatus;
        if (Sw360ResourceServer.IS_FORCE_UPDATE_ENABLED) {
        requestStatus = sw360PackageClient.updatePackageWithForceFlag(pkg, sw360User, true);
        } else {
            requestStatus = sw360PackageClient.updatePackage(pkg, sw360User);
        }

        if (requestStatus == RequestStatus.INVALID_INPUT) {
            throw new HttpMessageNotReadableException("Dependent document Id/ids not valid.");
        } else if (requestStatus == RequestStatus.NAMINGERROR) {
            throw new HttpMessageNotReadableException(
                    "Package name and version field cannot be empty or contain only whitespace character");
        } else if (requestStatus != RequestStatus.ACCESS_DENIED && requestStatus != RequestStatus.SUCCESS) {
            throw new RuntimeException(
                    "sw360 Package with name '" + SW360Utils.printName(pkg) + " cannot be updated.");
        }
        return requestStatus;
    }

    public RequestStatus deletePackage(String packageId, User sw360User) throws TException {
        PackageService.Iface sw360PackageClient = getThriftPackageClient();
        if (Sw360ResourceServer.IS_FORCE_UPDATE_ENABLED) {
            return sw360PackageClient.deletePackageWithForceFlag(packageId, sw360User, true);
        } else {
            return sw360PackageClient.deletePackage(packageId, sw360User);
        }
    }

    public PackageService.Iface getThriftPackageClient() throws TTransportException {
        PackageService.Iface packageClient = new ThriftClients().makePackageClient();
        return packageClient;
    }

    public Package getPackageForUserById(String id) throws TException {
            PackageService.Iface sw360PackageClient = getThriftPackageClient();
            try {
                return sw360PackageClient.getPackageById(id);
            } catch (SW360Exception sw360Exp) {
                if (sw360Exp.getErrorCode() == 404) {
                    throw new ResourceNotFoundException("Package does not exist! id=" + id);
                } else {
                    throw sw360Exp;
                }
        }
    }
}



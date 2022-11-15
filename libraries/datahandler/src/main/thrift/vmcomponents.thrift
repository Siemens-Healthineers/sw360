/*
 * Copyright Siemens AG, 2016-2018. Part of the SW360 Portal Project.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License Version 2.0 as published by the
 * Free Software Foundation with classpath exception.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License version 2.0 for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program (please see the COPYING file); if not, write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */
include "sw360.thrift"
include "users.thrift"

namespace java com.siemens.sw360.datahandler.thrift.vmcomponents
namespace php sw360.thrift.vmcomponents

typedef sw360.RequestSummary RequestSummary
typedef users.User User

enum VMMatchState {
    MATCHING_LEVEL_1 = 1, // 1 of 3 text matches found
    MATCHING_LEVEL_2 = 2, // 2 of 3 text matches found
    MATCHING_LEVEL_3 = 3, // 3 of 3 text matches found
    ACCEPTED = 8, // matches via CPE or manually accepted on UI
    DECLINED = 9, // declined on UI
}

enum VMMatchType {
    CPE = 1,
    SVM_ID = 2,
    NAME_CR = 10,
    NAME_RC = 11,
    VENDOR_CR = 20,
    VENDOR_RC = 21,
    VERSION_CR = 30,
    VERSION_RC = 31,
}

struct VMProcessReporting{
    // General information
    1: optional string id,
    2: optional string revision,
    3: optional string type = "vmprocessreporting",

    // Additional information
    10: required string elementType,
    11: required string startDate,
    12: optional string endDate,
    13: optional i32 processingSeconds,

    //statistics
    21: optional i32 idsReceived = 0;
    22: optional i32 newReceived = 0;
    23: optional i32 knownReceived = 0;
    24: optional i32 completed = 0;
}

struct VMPriority{
    // General information
    1: optional string id,
    2: optional string revision,
    3: optional string type = "vmpriority",
    4: optional string lastUpdateDate,

    // Additional information
    10: required string vmid,       // 2
    11: optional string shortText,  // "short_text": "major",
    12: optional string longText,   // "long_text":  "Typically used for remote or local attacks with significant impact but no full compromise of system. Investigate as soon as possible and follow recommended action."
}

struct VMAction{
    // General information
    1: optional string id,
    2: optional string revision,
    3: optional string type = "vmaction",
    4: optional string lastUpdateDate,

    // Additional information
    10: required string vmid,   // 5
    11: optional string text,   // "text": "Install New Package"
}

struct VMComponent{
    // Basic information
    1: optional string id,
    2: optional string revision,
    3: optional string type = "vmcomponent",
    5: required string receivedDate,
    6: optional string lastUpdateDate,

    // Additional information
    10: required string vmid,       // 70
    11: optional string vendor,     // "vendor":         "Apache Software Foundation",
    12: optional string name,       // "component_name": "Tomcat",
    13: optional string version,    // "version":        "3.2.1",
    14: optional string url         // "url":            "http://tomcat.apache.org/",
    15: optional string securityUrl,// "security_url":   null,
    16: optional bool eolReached, // "eol_reached":    true,
    17: optional string cpe,        // "cpe_name":       "cpe:/a:apache:tomcat:3.2.1",

    // additional information about patches
    20: optional set<VMMinPatchLevel> minPatchLevels, // "minimum_patch_levels": {"priority_1": null,"priority_2": "6.0.36","priority_3": null }
}

struct VMMinPatchLevel{
    // Basic information
    1: optional string id,
    2: optional string revision,
    3: optional string type = "vmminpatchlevel",

    // Additional information
    10: required string priority, // vmid of vmpriority
    11: optional string version,
}

struct VMMatch{
    // Basic information
    1: optional string id,
    2: optional string revision,
    3: optional string type = "vmmatch",

    // vm component information
    11: required string vmComponentId;
    12: optional string vmComponentCpe;
    13: optional string vmComponentName;
    14: optional string vmComponentVendor;
    15: optional string vmComponentVersion;
    16: optional string vmComponentVmid;

    // sw360 release information
    21: required string releaseId;
    22: optional string releaseCpe;
    23: optional string componentName;
    24: optional string vendorName;
    25: optional string releaseVersion;
    26: optional string releaseSvmId;

    // matching information
    31: required set<VMMatchType> matchTypes,
        32: optional string matchTypesUI;
    33: required VMMatchState state,
}

service VMComponentService {
    // General information
    list<VMProcessReporting> getAllProcesses(1: User user);
    list<VMMatch> getAllMatches(1: User user);

    // Trigger for vulnerability monitoring
    RequestSummary synchronizeComponents(/*1: User user*/);
    RequestSummary triggerReverseMatch(/*1: User user*/);
//    RequestSummary scheduleSync(1: User user);
//    RequestSummary unscheduleSync(1: User user);
    RequestSummary acceptMatch(1: User user, 2: string matchId);
    RequestSummary declineMatch(1: User user, 2: string matchId);
}
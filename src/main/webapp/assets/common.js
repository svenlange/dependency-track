/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */

"use strict";

const $common = function() {
};

/**
 * Defines JSON characters that need to be escaped when data is used in HTML
 */
const __entityMap = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
    "/": "&#x2F;"
};

/**
 * Returns a function, that, as long as it continues to be invoked, will not
 * be triggered. The function will be called after it stops being called for
 * N milliseconds. If `immediate` is passed, trigger the function on the
 * leading edge, instead of the trailing.
 *
 * https://davidwalsh.name/javascript-debounce-function
 *
 * @callback callback
 * @param {callback} func the function to call
 * @param {number} wait the time to wait
 * @param {boolean} immediate
 */
$common.debounce = function debounce(func, wait, immediate) {
    let timeout;
    return function() {
        let context = this, args = arguments;
        let later = function() {
            timeout = null;
            if (!immediate) {
                func.apply(context, args);
            }
        };
        let callNow = immediate && !timeout;
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
        if (callNow) {
            func.apply(context, args);
        }
    };
};

/**
 * Useful for parsing HREF's
 */
$common.getLocation = function getLocation(url) {
    let href = (url === null) ? window.location : url;
    let location = document.createElement("a");
    location.href = href;
    return location;
};

/**
 * Check if a string is empty, null or undefined
 */
$common.isEmpty = function isEmpty(string) {
    return (!string || 0 === string.length);
};

/**
 * Check if a string is blank, null or undefined I use:
 */
$common.isBlank = function isBlank(string) {
    return (!string || /^\s*$/.test(string));
};

//######################################################################################################################
/**
 * Called after we have veri fied that a user is authenticated (if authentication is enabled)
 */
$common.initialize = function initialize() {
    const token = $auth.decodeToken($auth.getToken());

    if ($auth.hasPermission($auth.VIEW_PORTFOLIO, token)) {
        $("#content-container.require-view-portfolio").css("display", "block");
    }
    if ($auth.hasPermission($auth.ACCESS_MANAGEMENT, token) || $auth.hasPermission($auth.SYSTEM_CONFIGURATION, token)) {
        $("#sidebar-admin-button").css("display", "block");
    }
    if ($auth.hasPermission($auth.ACCESS_MANAGEMENT, token)) {
        $("div.require-access-management").css("display", "block");
    }
    if ($auth.hasPermission($auth.SYSTEM_CONFIGURATION, token)) {
        $("div.require-system-configuration").css("display", "block");
    }
    if ($auth.hasPermission($auth.PORTFOLIO_MANAGEMENT, token)) {
        $("button.require-portfolio-management").css("display", "inline-block");
        $(".require-portfolio-management").removeAttr("disabled");
        $(".refresh-metric.require-portfolio-management").css("display", "inline-block");
    }
    if ($auth.hasPermission($auth.VULNERABILITY_ANALYSIS, token)) {
        $("li.require-vulnerability-analysis").css("display", "block");
    }

    $rest.getVersion(
        /**
         * @param {Object} data JSON response object
         * @param data.application the name of the application
         * @param data.version the version of the application
         * @param data.timestamp the timestamp in which the application was built
         * @param data.framework.name the name of the framework
         * @param data.framework.version the version of the framework
         * @param data.framework.timestamp in which the framework was built
         * @method $ jQuery selector
         */
        function onVersionSuccess(data) {
            // Populates teh system modeal with general app info
            $("#systemAppName").html(data.application);
            $("#systemAppVersion").html(data.version);
            $("#systemAppBuiltOn").html($common.formatTimestamp(data.timestamp, true));
            $("#systemAppBuildId").html(data.uuid);
            $("#systemFrameworkName").html(data.framework.name);
            $("#systemFrameworkVersion").html(data.framework.version);
            $("#systemFrameworkBuiltOn").html($common.formatTimestamp(data.framework.timestamp, true));
            $("#systemFrameworkBuildId").html(data.framework.uuid);

            if (!$.sessionStorage.isSet("token")) {
                $("#nav-logout").css("display", "none");
            }

            // SNAPSHOT release notification
            if (data.version.includes("SNAPSHOT") && !$.sessionStorage.isSet("snapshot")) {
                $("#modal-snapshotNotification").modal();
                $.sessionStorage.set("snapshot", "true");
            }
        }
    );
    $common.unloadSpinner();
};

$common.unloadSpinner = function unloadSpinner() {
    $('#loader').css('display', 'none');
    $('#navbar-container').css('display', 'block');
};

/**
 * Logout function removes the stored jwt token and reloads the page, which will
 * force the login modal to display
 */
$common.logout = function logout() {
    // Instructs all tabs (via localStorage event) that the session is being invalidated
    $.localStorage.set("sessionInvalidate", Date.now());
    $.localStorage.remove("sessionInvalidate");
    // Removes the token from session storage and reload
    $.sessionStorage.remove("token");
    location.reload();
};

/**
 * Executed when the login button is clicked. Prevent the form from actually being
 * submitted and uses javascript to submit the form info.
 *
 * @method $ jQuery selector
 */
$("#login-form").submit(function(event) {
    event.preventDefault();
    let usernameElement = $("#username");
    let username = usernameElement.val();
    let passwordElement = $("#password");
    let password = passwordElement.val();
    $rest.login(username, password, function(data) {
        $.sessionStorage.set("token", data);
        // Hack to fix the loading of content after login as defined in:
        // https://github.com/DependencyTrack/dependency-track/issues/167
        // todo: should be removed in the future - especially once the next gen UI (SPA) is available
        window.location.reload(false); // Reload from browser cache

        //$("#navbar-container").css("display", "block");
        //$("#sidebar").css("display", "block");
        //$(".main").css("display", "block");
        //$("#modal-login").modal("hide");
        //$common.initialize();
    }, function(data) {
        switch (data.responseText) {
            case "INVALID_CREDENTIALS":
                $common.displayInfoModal("Invalid username or password");
                break;
            case "EXPIRED_CREDENTIALS":
                $common.displayInfoModal("The supplied credential have expired");
                break;
            case "FORCE_PASSWORD_CHANGE":
                $("#modal-login").modal("hide");
                $("#modal-forcePasswordChange").modal("show");
                break;
            case "SUSPENDED":
                $common.displayInfoModal("This account has been suspended and is no longer active");
                break;
            case "UNMAPPED_ACCOUNT":
                $common.displayInfoModal("This account does not have access to Dependency-Track");
                break;
            default:
                $common.displayInfoModal("Unable to authenticate. Contact your Dependency-Track administrator");
        }
    });
    usernameElement.val("");
    passwordElement.val("");
});

/**
 * Executed when the change password button is clicked. Prevent the form from actually being
 * submitted and uses javascript to submit the form info.
 *
 * @method $ jQuery selector
 */
$("#forcePasswordChange-form").submit(function(event) {
    event.preventDefault();
    let usernameElement = $("#forcePasswordChange-username");
    let username = usernameElement.val();
    let passwordElement = $("#forcePasswordChange-password");
    let password = passwordElement.val();
    let newPasswordElement = $("#forcePasswordChange-newPassword");
    let newPassword = newPasswordElement.val();
    let confirmPasswordElement = $("#forcePasswordChange-confirmPassword");
    let confirmPassword = confirmPasswordElement.val();
    $("#modal-forcePasswordChange").modal("hide");
    $rest.forceChangePassword(username, password, newPassword, confirmPassword, function(data) {
        $common.displayInfoModal("Password successfully changed");
        $("#modal-forcePasswordChange").modal("hide");
        $("#modal-login").modal("show");
    }, function(data) {
        $("#modal-forcePasswordChange").modal("show");
        $common.displayInfoModal(data.responseText);
    });
    usernameElement.val("");
    passwordElement.val("");
    newPasswordElement.val("");
    confirmPasswordElement.val("");
});

/**
 * Displays an error modal with the specified message. If the responseText
 * from an AJAX response is available, that text will be used. If not, the
 * fallback message will be used instead.
 */
$common.displayErrorModal = function displayErrorModal(xhr, fallbackMessage) {
    let message = fallbackMessage;
    if (xhr && xhr.responseText && xhr.responseText.trim()) {
        message = xhr.responseText.trim();
    }
    $("#modal-genericError").modal("show");
    $("#modal-genericErrorContent").text(message);
};

/**
 * Displays an informational modal with the specified message.
 */
$common.displayInfoModal = function displayInfoModal(message) {
    $("#modal-informational").modal("show");
    $("#modal-infoMessage").text(message);
};

/**
 * Creates an informational, single-color progress bar.
 */
$common.generateProgressBar = function generateProgressBar(count, total) {
    let percent = (count/total)*100;
    let block = '<span class="progress">';
    block += '<div class="progress-bar severity-info-bg" data-toggle="tooltip" data-placement="top" title="' + count + '" style="width:' + percent + '%">' + count + '</div>';
    block += '</span>';
    return block;
};

/**
 * Creates a multi-color progress bar consisting of the number of
 * critical, high, medium, low, and unassigned severity vulnerabilities.
 */
$common.generateSeverityProgressBar = function generateSeverityProgressBar(critical, high, medium, low, unassigned) {
    let percentCritical = (critical/(critical+high+medium+low+unassigned))*100;
    let percentHigh = (high/(critical+high+medium+low+unassigned))*100;
    let percentMedium = (medium/(critical+high+medium+low+unassigned))*100;
    let percentLow = (low/(critical+high+medium+low+unassigned))*100;
    let percentUnassigned = (unassigned/(critical+high+medium+low+unassigned))*100;
    let block = '<span class="progress">';
    if (critical > 0) {
        block += '<div class="progress-bar severity-critical-bg" data-toggle="tooltip" data-placement="top" title="Critical: ' + critical + ' (' + Math.round(percentCritical*10)/10 + '%)" style="width:' + percentCritical+ '%">' + critical + '</div>';
    }
    if (high > 0) {
        block += '<div class="progress-bar severity-high-bg" data-toggle="tooltip" data-placement="top" title="High: ' + high + ' (' + Math.round(percentHigh * 10) / 10 + '%)" style="width:' + percentHigh + '%">' + high + '</div>';
    }
    if (medium > 0) {
        block += '<div class="progress-bar severity-medium-bg" data-toggle="tooltip" data-placement="top" title="Medium: ' + medium + ' (' + Math.round(percentMedium * 10) / 10 + '%)" style="width:' + percentMedium + '%">' + medium + '</div>';
    }
    if (low > 0) {
        block += '<div class="progress-bar severity-low-bg" data-toggle="tooltip" data-placement="top" title="Low: ' + low + ' (' + Math.round(percentLow * 10) / 10 + '%)" style="width:' + percentLow + '%">' + low + '</div>';
    }
    if (unassigned > 0) {
        block += '<div class="progress-bar severity-unassigned-bg" data-toggle="tooltip" data-placement="top" title="Unassigned: ' + unassigned + ' (' + Math.round(percentUnassigned * 10) / 10 + '%)" style="width:' + percentUnassigned + '%">' + unassigned + '</div>';
    }
    if (critical === 0 && high === 0 && medium === 0 && low === 0 && unassigned === 0) {
        block += '<div class="progress-bar severity-info-bg" data-toggle="tooltip" data-placement="top" title="No Vulnerabilities Detected" style="width:100%">0</div>';
    }
    block += '</span>';
    return block;
};

/**
 * Given a UNIX timestamp, this function will return a formatted date.
 * i.e. 15 Jan 2017
 */
$common.formatTimestamp = function formatTimestamp(timestamp, includeTime) {
    let date = new Date(timestamp);
    let months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    function pad(num) { return num < 10 ? "0" + num : num; }
    if (includeTime) {
        return date.getDate() + " " + months[date.getMonth()] + " " + date.getFullYear() + " at " + pad(date.getHours()) + ":" + pad(date.getMinutes()) + ":" + pad(date.getSeconds());
    } else {
        return date.getDate() + " " + months[date.getMonth()] + " " + date.getFullYear();
    }
};

/**
 * Formats and returns a specialized label for a vulnerability source (NVD, NSP, VulnDB, OSSIndex etc).
 */
$common.formatSourceLabel = function formatSourceLabel(source) {
    let sourceClass = "label-source-" + source.toLowerCase();
    return `<span class="label ${sourceClass}">${source}</span>`;
};

/**
 * Formats and returns a specialized label for the severity of a vulnerability.
 */
$common.formatSeverityLabel = function formatSeverityLabel(severity) {
    if (!severity) {
        return "";
    }
    let severityLabel = $common.capitalize(severity);
    let severityClass = "severity-" + severity.toLowerCase() + "-bg";

    return `
     <div style="height:24px;margin:-4px;">
        <div class="${severityClass} text-center pull-left" style="width:24px; height:24px; color:#ffffff">
            <i class="fa fa-bug" style="font-size:12px; padding:6px" aria-hidden="true"></i>
         </div>
         <div class="text-center pull-left" style="height:24px;">
             <div style="font-size:12px; padding:4px"><span class="severity-value">${severityLabel}</span></div>
         </div>
     </div>`;
};

/**
 * Changes the first letter to uppercase and the remaining letters to lowercase.
 *
 * @param {string} string the String to capitalize
 */
$common.capitalize = function capitalize(string) {
    if (string && string.length > 2) {
        return string.charAt(0).toUpperCase() + string.slice(1).toLowerCase();
    }
    return string;
};

/**
 * Helper function that returns the variable if it is not null, undefined, NaN,
 * an empty string (""), 0, or false. Otherwise, returns the default value.
 */
$common.valueWithDefault = function valueWithDefault(variable, defaultValue) {
    if (variable) {
        return variable;
    } else {
        return defaultValue;
    }
};

/**
 * Given a comma-separated string, creates an array of objects.
 */
$common.csvStringToObjectArray = function csvStringToObjectArray(csvString) {
    let csvArray = [];
    if (!$common.isEmpty(csvString)) {
        let tmpArray = csvString.split(",");
        for (let i in tmpArray) {
            if (tmpArray.hasOwnProperty(i)) {
                csvArray.push({name: tmpArray[i]});
            }
        }
    }
    return csvArray;
};

/**
 * Perform client-side JSON escaping
 */
$common.toHtml = function toHtml(string) {
    if(typeof string === "string") {
        return String(string).replace(/[&<>"'\/]/g, function (s) {
            return __entityMap[s];
        });
    } else {
        return string;
    }
};

/**
 * Populates the user profile modal with data from the current logged in user.
 */
$common.populateUserProfileData = function populateUserProfileData(data) {
    $("#profileUsernameInput").val(filterXSS(data.username));
    $("#profileFullnameInput").val(filterXSS(data.fullname));
    $("#profileEmailInput").val(filterXSS(data.email));
    $("#profileNewPasswordInput").val("");
    $("#profileConfirmPasswordInput").val("");
};

/**
 * Encodes the supplied data in Base64 and returns the encoded string.
 */
$common.base64Encode = function base64Encode(data) {
    return btoa(data);
};

/**
 * Decodes the supplied Base64 encoded data and returns the decoded string.
 */
$common.base64Decode = function base64Encode(data) {
    return atob(data);
};

$common.bootstrapInputFile = function bootstrapInputFile(id) {
    $(".input-file").before(
        function() {
            if ( ! $(this).prev().hasClass('input-ghost') ) {
                var element = $("<input id='" + id + "' type='file' class='input-ghost' style='visibility:hidden; height:0'>");
                element.attr("name",$(this).attr("name"));
                element.change(function() {
                    element.next(element).find('input').val((element.val()).split('\\').pop());
                });
                $(this).find("button.btn-choose").click(function() {
                    element.click();
                });
                $(this).find("button.btn-reset").click(function() {
                    element.val(null);
                    $(this).parents(".input-file").find('input').val('');
                });
                $(this).find('input').css("cursor","pointer");
                $(this).find('input').mousedown(function() {
                    $(this).parents('.input-file').prev().click();
                    return false;
                });
                return element;
            }
        }
    );
};

/**
 * Given a total number of something and a lower number that is completed,
 * function will return a percentage rounded to the tenth decimal place.
 */
$common.calcProgressPercent = function calcProgressPercent(total, completed) {
    if (total > 0) {
        if (completed === 0) {
            return 0;
        } else {
            let percentage = (completed / total) * 100;
            return Math.round(percentage * 10) / 10;
        }
    } else if (completed > total) {
        // In something has already been completed (e.g. suppressed) and the completed value
        // is greater than the total, return 100%
        return 100;
    }
    return 0; // the absence of work does not imply progress.
};

$common.calcProgressPercentLabel = function calcProgressPercentLabel(total, completed) {
    let progress = $common.calcProgressPercent(total, completed);
    return progress + "%";
};

$common.toastrOptions =
    {
        "closeButton": true,
        "debug": false,
        "newestOnTop": false,
        "progressBar": false,
        "positionClass": "toast-top-right",
        "preventDuplicates": true,
        "onclick": null,
        "showDuration": "300",
        "hideDuration": "1000",
        "timeOut": "5000",
        "extendedTimeOut": "1000",
        "showEasing": "swing",
        "hideEasing": "linear",
        "showMethod": "fadeIn",
        "hideMethod": "fadeOut"
    };

/**
 * Executed when the DOM is ready for JavaScript to be executed.
 */
$(document).ready(function () {

    // Initialize all tooltips
    $('[data-toggle="tooltip"]').tooltip();

    // Get information about the current logged in user (if available)
    $rest.getPrincipalSelf(
        function(data) {
            $common.populateUserProfileData(data);
            $common.initialize();
        });
    let contextPath = $rest.contextPath();

    // Listen for the update profile button being pressed
    $("#updateProfileButton").on("click", function () {
        let fullname = $("#profileFullnameInput").val();
        let email = $("#profileEmailInput").val();
        let newPassword = $("#profileNewPasswordInput").val();
        let confirmPassword = $("#profileConfirmPasswordInput").val();
        $rest.updatePrincipalSelf(fullname, email, newPassword, confirmPassword, function(data) {
            $common.populateUserProfileData(data);
        });
    });

    /**
     * Function that adds the 'active' class to one of the buttons in
     * the sidebar based on the data-sidebar attribute in the pages' body.
     */
    (function() {
        let nav = document.getElementById("sidebar"),
            anchors = nav.getElementsByTagName("a"),
            bodySidebar = document.body.getAttribute("data-sidebar");

        for (let i = 0; i < anchors.length; i++) {
            if(bodySidebar === anchors[i].getAttribute("data-sidebar")) {
                anchors[i].parentElement.className = "active";
            }
        }
    })();

    $("#smart-search-input").typeahead(null,
        {
            name: "project",
            source: $rest.smartsearchProject(),
            display: "name",
            templates: {
                header: '<h4 class="section-title">Projects</h4>',
                /**
                 * @param data the JSON data returned
                 * @param data.uuid the UUID of the object
                 * @param data.name the name of the object
                 * @returns {string}
                 */
                suggestion: function (data) {
                    return '<a class="tt-suggestion-item" href="' + contextPath + 'project/?uuid=' + filterXSS(data.uuid) + '">' + filterXSS(data.name) + '</a>';
                }
            }
        },
        {
            name: "component",
            source: $rest.smartsearchComponent(),
            display: "name",
            templates: {
                header: '<h4 class="section-title">Components</h4>',
                /**
                 * @param data the JSON data returned
                 * @param data.uuid the UUID of the object
                 * @param data.name the name of the object
                 * @returns {string}
                 */
                suggestion: function (data) {
                    return '<a class="tt-suggestion-item" href="' + contextPath + 'component/?uuid=' + filterXSS(data.uuid) + '">' + filterXSS(data.name) + '</a>';
                }
            }
        },
        {
            name: "vulnerability",
            source: $rest.smartsearchVulnerability(),
            display: "vulnId",
            templates: {
                header: '<h4 class="section-title">Vulnerabilities</h4>',
                /**
                 * @param data the JSON data returned
                 * @param data.source the source of of the vulnerability
                 * @param data.vulnId the ID unique to the source
                 * @returns {string}
                 */
                suggestion: function (data) {
                    return '<a class="tt-suggestion-item" href="' + contextPath + 'vulnerability/?source=' + filterXSS(data.source) + '&vulnId=' + filterXSS(data.vulnId) + '">' + filterXSS(data.vulnId) + '</a>';
                }
            }
        },
        {
            name: "license",
            source: $rest.smartsearchLicense(),
            display: "name",
            templates: {
                header: '<h4 class="section-title">Licenses</h4>',
                /**
                 * @param data the JSON data returned
                 * @param data.licenseId unique license ID
                 * @param data.name the name of the object
                 * @returns {string}
                 */
                suggestion: function (data) {
                    return '<a class="tt-suggestion-item" href="' + contextPath + 'license/?licenseId=' + filterXSS(data.licenseId) + '">' + filterXSS(data.name) + '</a>';
                }
            }
        }
    );

});


/**
 * Extends JQuery
 */
$.extend({

    /**
     * Retrieves the querystring, parses it.
     */
    getUrlVars: function() {
        let vars = [], hash;
        let hashes = window.location.href.replace("#", "").slice(window.location.href.indexOf("?") + 1).split("&");
        for(let i = 0; i < hashes.length; i++) {
            hash = hashes[i].split("=");
            vars.push(hash[0]);
            vars[hash[0]] = hash[1];
        }
        return vars;
    },

    /**
     * Provides a function to extract a param from the querystring.
     */
    getUrlVar: function(name) {
        return $.getUrlVars()[name];
    }
});

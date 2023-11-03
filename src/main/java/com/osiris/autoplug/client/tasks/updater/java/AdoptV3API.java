/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.updater.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.osiris.jlib.json.Json;
import com.osiris.jlib.json.exceptions.HttpErrorException;
import com.osiris.jlib.json.exceptions.WrongJsonTypeException;

import java.io.IOException;
import java.util.function.Function;

/**
 * Details here: https://api.adoptium.net/q/swagger-ui
 */
public class AdoptV3API {
    private final String START_DOWNLOAD_URL = "https://api.adoptium.net/v3/binary/version/";
    private final String START_RELEASES_URL = "https://api.adoptium.net/v3/info/release_versions?architecture=";
    private final String START_ASSETS_URL = "https://api.adoptium.net/v3/assets/version/";

    /**
     * Creates and returns a new url from the provided parameters. <br>
     * For a list of all available parameters types see: https://api.adoptium.net/q/swagger-ui/#/Assets/searchReleasesByVersion
     *
     * @param releaseVersionName Example: 11.0.4.1+11.1
     * @param isLargeHeapSize    If true allows your jvm to use more that 57gb of ram.
     * @param isHotspotImpl      If true uses hotspot, otherwise the openj9 implementation.
     * @param isOnlyLTS          If true only shows LTS (Long Term Support) releases.
     * @param maxItems           Example: 20
     * @return
     */
    public String getVersionInformationUrl(String releaseVersionName, OperatingSystemArchitectureType osArchitectureType, boolean isLargeHeapSize, ImageType imageType,
                                           boolean isHotspotImpl, boolean isOnlyLTS, OperatingSystemType osType, int maxItems,
                                           VendorProjectType vendorProject, ReleaseType releaseType) {
        String jvmImplementation = isHotspotImpl ? "hotspot" : "openj9";
        String heapSize = isLargeHeapSize ? "large" : "normal";
        return START_ASSETS_URL
                + "%5B" + releaseVersionName
                .replace(".LTS", "")
                .replace(".EA", "")
                .replace("+", "%2B") + "%2C%29"
                + "?architecture=" + osArchitectureType.name
                + "&heap_size=" + heapSize
                + "&image_type=" + imageType.name
                + "&jvm_impl=" + jvmImplementation
                + "&lts=" + isOnlyLTS
                + "&os=" + osType.name
                + "&page=0"
                + "&page_size=" + maxItems
                + "&project=" + vendorProject.name
                + "&release_type=" + releaseType.name
                + "&sort_method=DEFAULT&sort_order=DESC"
                + "&vendor=eclipse";
    }

    public JsonArray getVersionInformation(String releaseVersionName, OperatingSystemArchitectureType osArchitectureType, boolean isLargeHeapSize, ImageType imageType,
                                           boolean isHotspotImpl, boolean isOnlyLTS, OperatingSystemType osType, int maxItems,
                                           VendorProjectType vendorProject, ReleaseType releaseType) throws WrongJsonTypeException, IOException, HttpErrorException {
        return Json.getAsJsonArray(getVersionInformationUrl(
                releaseVersionName, osArchitectureType, isLargeHeapSize, imageType, isHotspotImpl,
                isOnlyLTS, osType, maxItems, vendorProject, releaseType
        ));
    }

    /**
     * Creates and returns a new url from the provided parameters. <br>
     * For a list of all available parameters types see: https://api.adoptium.net/q/swagger-ui/#/Release%20Info/getReleaseVersions
     *
     * @param isLargeHeapSize If true allows your jvm to use more that 57gb of ram.
     * @param isHotspotImpl   If true uses hotspot, otherwise the openj9 implementation.
     * @param isOnlyLTS       If true only shows LTS (Long Term Support) releases.
     * @param maxItems        Example: 20
     * @return
     */
    public String getReleasesUrl(int page, OperatingSystemArchitectureType osArchitectureType, boolean isLargeHeapSize, ImageType imageType,
                                 boolean isHotspotImpl, boolean isOnlyLTS, OperatingSystemType osType, int maxItems,
                                 VendorProjectType vendorProject, ReleaseType releaseType) {
        String jvmImplementation = isHotspotImpl ? "hotspot" : "openj9";
        String heapSize = isLargeHeapSize ? "large" : "normal";
        return START_RELEASES_URL
                + osArchitectureType.name
                + "&heap_size=" + heapSize
                + "&image_type=" + imageType.name
                + "&jvm_impl=" + jvmImplementation
                + "&lts=" + isOnlyLTS
                + "&os=" + osType.name
                + "&page=" + page
                + "&page_size=" + maxItems
                + "&project=" + vendorProject.name
                + "&release_type=" + releaseType.name
                + "&sort_method=DEFAULT&sort_order=DESC"
                + "&vendor=eclipse";
    }

    public void getReleases(OperatingSystemArchitectureType osArchitectureType, boolean isLargeHeapSize, ImageType imageType,
                            boolean isHotspotImpl, boolean isOnlyLTS, OperatingSystemType osType, int maxItems,
                            VendorProjectType vendorProject, ReleaseType releaseType, Function<JsonObject, Boolean> onNewPage) throws WrongJsonTypeException, IOException, HttpErrorException {
        String url = null;
        int page = 0;
        try {
            while (true) { // Loop through all pages until last request gives 404 error code
                url = getReleasesUrl(page, osArchitectureType, isLargeHeapSize, imageType,
                        isHotspotImpl, isOnlyLTS, osType, maxItems, vendorProject, releaseType);
                Boolean shouldContinue = onNewPage.apply(Json.getAsObject(url));
                if (!shouldContinue) break;
                page++;
            }
        } catch (HttpErrorException e) {
            if (e.getHttpErrorCode() != 404) // 404 == Page not found
                throw e;
        }
    }

    /**
     * Creates and returns a new url from the provided parameters. <br>
     * For a list of all available parameters types see: https://api.adoptium.net/q/swagger-ui/#/Binary/getBinaryByVersion
     *
     * @param releaseName     Note that this is not the regular version name. Example: jdk-15.0.2+7
     * @param isHotspotImpl   If true uses hotspot, otherwise the openj9 implementation.
     * @param isLargeHeapSize If true allows your jvm to use more that 57gb of ram.
     */
    public String getDownloadUrl(String releaseName, OperatingSystemType osType, OperatingSystemArchitectureType osArchitectureType,
                                 ImageType imageType, boolean isHotspotImpl, boolean isLargeHeapSize,
                                 VendorProjectType vendorProject) {
        String jvmImplementation = isHotspotImpl ? "hotspot" : "openj9";
        String heapSize = isLargeHeapSize ? "large" : "normal";
        return START_DOWNLOAD_URL
                + releaseName + "/"
                + osType.name + "/"
                + osArchitectureType.name + "/"
                + imageType.name + "/"
                + jvmImplementation + "/"
                + heapSize + "/"
                + "eclipse?project=" + vendorProject.name;
    }


    // ENUMS:


    public enum VendorProjectType {
        JDK("jdk"),
        VALHALLA("valhalla"),
        METROPOLIS("metropolis"),
        JFR("jfr"),
        SHENANDOAH("shenandoah");

        private final String name;

        VendorProjectType(String name) {
            this.name = name;
        }
    }

    public enum ImageType {
        JDK("jdk"),
        JRE("jre"),
        TEST_IMAGE("testimage"),
        DEBUG_IMAGE("debugimage"),
        STATIC_LIBS("staticlibs");

        private final String name;

        ImageType(String name) {
            this.name = name;
        }
    }

    public enum OperatingSystemArchitectureType {
        X64("x64"),
        X86("x86"),
        X32("x32"),
        PPC64("ppc64"),
        PPC64LE("ppc64le"),
        S390X("s390x"),
        AARCH64("aarch64"),
        ARM("arm"),
        SPARCV9("sparcv9"),
        RISCV64("riscv64"),
        // x64 with alternative names:
        AMD64("x64"),
        X86_64("x64"),
        // x32 with alternative names:
        I386("x32"),
        // AARCHx64 with alternative names:
        ARM64("aarch64");

        private final String name;

        OperatingSystemArchitectureType(String name) {
            this.name = name;
        }
    }

    public enum OperatingSystemType {
        LINUX("linux"),
        WINDOWS("windows"),
        MAC("mac"),
        SOLARIS("solaris"),
        AIX("aix"),
        ALPINE_LINUX("alpine-linux");

        private final String name;

        OperatingSystemType(String name) {
            this.name = name;
        }
    }

    public enum ReleaseType {
        GENERAL_AVAILABILITY("ga"),
        EARLY_ACCESS("ea");

        private final String name;

        ReleaseType(String name) {
            this.name = name;
        }
    }
}

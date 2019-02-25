/* Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved. */

package com.oracle.weblogicx.imagebuilder.cli.cache;

import com.oracle.weblogicx.imagebuilder.api.model.CommandResponse;
import com.oracle.weblogicx.imagebuilder.api.model.WLSInstallerType;
import com.oracle.weblogicx.imagebuilder.util.ARUUtil;
import com.oracle.weblogicx.imagebuilder.util.Constants;
import com.oracle.weblogicx.imagebuilder.util.SearchResult;
import com.oracle.weblogicx.imagebuilder.util.Utils;
import com.oracle.weblogicx.imagebuilder.util.XPathUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.w3c.dom.Document;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.oracle.weblogicx.imagebuilder.api.meta.CacheStore.CACHE_KEY_SEPARATOR;

@Command(
        name = "addPatch",
        description = "Add cache entry for wls|fmw patch or psu",
        sortOptions = false
)
public class AddPatchEntry extends CacheOperation {

    String password;

    public AddPatchEntry() {
    }

    public AddPatchEntry(boolean isCLIMode) {
        super(isCLIMode);
    }

    @Override
    public CommandResponse call() throws Exception {
        password = handlePasswordOptions();
        if (patchId != null && !patchId.isEmpty()
                && userId != null && !userId.isEmpty()
                && password != null && !password.isEmpty()
                && location != null && Files.exists(location) && Files.isRegularFile(location)) {
            String patchNumber = this.patchId.toLowerCase();
            if (patchNumber.startsWith("p") && patchNumber.length() > 1) {
                patchNumber = patchNumber.substring(1);
            }
            SearchResult result = ARUUtil.getPatchDetail(type.toString(), version, patchNumber, userId, password);
            if (result.isSuccess()) {
                Document document = result.getResults();
                String patchDigest = XPathUtil.applyXPathReturnString(document, "string"
                        + "(/results/patch[1]/files/file/digest[@type='SHA-256']/text())");
                String localDigest = DigestUtils.sha256Hex(new FileInputStream(location.toFile()));

                if (localDigest.equalsIgnoreCase(patchDigest)) {
                    String releaseNumber = XPathUtil.applyXPathReturnString(document,
                            "string(/results/patch[1]/release/@id)");
                    String key = patchNumber + CACHE_KEY_SEPARATOR + releaseNumber;
                    cacheStore.addToCache(key, location.toAbsolutePath().toString());
                    return new CommandResponse(0, String.format(
                            "Added Patch entry %s=%s for %s", key, location.toAbsolutePath(), type));
                } else {
                    return new CommandResponse(-1, String.format(
                            "Local file sha-256 digest %s != patch digest %s", localDigest, patchDigest));
                }
            }
        } else {
            return new CommandResponse(-1, "Invalid arguments");
        }
        return null;
    }

    /**
     * Determines the support password by parsing the possible three input options
     *
     * @return String form of password
     * @throws IOException in case of error
     */
    private String handlePasswordOptions() throws IOException {
        return Utils.getPasswordFromInputs(passwordStr, passwordFile, passwordEnv);
    }

    @Option(
            names = {"--patchId"},
            description = "Patch number. Ex: p28186730",
            required = true
    )
    private String patchId;

    @Option(
            names = {"--type"},
            description = "Type of patch. Valid values: ${COMPLETION-CANDIDATES}",
            required = true,
            defaultValue = "wls"
    )
    private WLSInstallerType type;

    @Option(
            names = {"--version"},
            description = "version of mw this patch is for. Ex: 12.2.1.3.0",
            required = true,
            defaultValue = Constants.DEFAULT_WLS_VERSION
    )
    private String version;

    @Option(
            names = {"--path"},
            description = "Location on disk. For ex: /path/to/FMW/patch.zip",
            required = true
    )
    private Path location;

    @Option(
            names = {"--user"},
            paramLabel = "<support email>",
            required = true,
            description = "Oracle Support email id"
    )
    private String userId;

    @Option(
            names = {"--password"},
            paramLabel = "<password for support user id>",
            description = "Password for support userId"
    )
    String passwordStr;

    @Option(
            names = {"--passwordEnv"},
            paramLabel = "<environment variable>",
            description = "environment variable containing the support password"
    )
    String passwordEnv;

    @Option(
            names = {"--passwordFile"},
            paramLabel = "<password file>",
            description = "path to file containing just the password"
    )
    Path passwordFile;
}
package com.framstag.llmaj.tools.java;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Module {
    /**
     * The format of the raw module report. Bump this whenever a report loses data that a consumer needs,
     * so that a reused report is detected as outdated instead of quietly yielding empty results.
     *
     * <p>Version 2 added the weighted reference record and the per-method access counters.
     */
    public static final int CURRENT_REPORT_FORMAT_VERSION = 2;

    private final String name;
    private final int reportFormatVersion;
    private final List<Package> packages;

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Module(@JsonProperty("name")String name,
                  @JsonProperty("reportFormatVersion") Integer reportFormatVersion) {
        this.name = name;
        this.reportFormatVersion = reportFormatVersion == null ? 0 : reportFormatVersion;
        this.packages = new LinkedList<>();
    }

    public Module(String name) {
        this(name, CURRENT_REPORT_FORMAT_VERSION);
    }

    public String getName() {
        return name;
    }

    public int getReportFormatVersion() {
        return reportFormatVersion;
    }

    @JsonIgnore
    public boolean isReportFormatCurrent() {
        return reportFormatVersion == CURRENT_REPORT_FORMAT_VERSION;
    }

    public void addPackage(Package pck) {
        this.packages.add(pck);
    }

    public List<Package> getPackages() {
        return Collections.unmodifiableList(packages);
    }
}

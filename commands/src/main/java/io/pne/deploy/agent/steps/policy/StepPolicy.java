package io.pne.deploy.agent.steps.policy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What a plan is allowed to do on this host.
 *
 * <p>The policy belongs to the host, not to the plan: it is read locally and every plan is checked against it before
 * anything runs. A sender can therefore ask only for actions the host already agreed to - downloading from a known
 * source into a known directory, writing inside a known tree, signalling a known service.
 */
public class StepPolicy {

    private final boolean       allowShell;
    private final String        shellAllowedPrefix;
    private final HostAllowList fetchHosts;
    private final HostAllowList statusHosts;
    private final PathRoots     writeRoots;
    private final PathRoots     readRoots;
    private final PathRoots     serviceDirs;
    private final Path          serviceControlBinary;
    private final long          maxFetchBytes;
    private final long          maxUnpackBytes;
    private final int           maxUnpackEntries;
    private final int           maxStepSeconds;
    private final int           maxSleepSeconds;
    private final int           maxPlanSeconds;

    private StepPolicy(Builder aBuilder) {
        allowShell           = aBuilder.allowShell;
        shellAllowedPrefix   = aBuilder.shellAllowedPrefix;
        fetchHosts           = new HostAllowList(aBuilder.fetchHosts);
        statusHosts          = new HostAllowList(aBuilder.statusHosts.isEmpty() ? aBuilder.fetchHosts : aBuilder.statusHosts);
        writeRoots           = new PathRoots(aBuilder.writeRoots);
        readRoots            = new PathRoots(aBuilder.readRoots.isEmpty() ? aBuilder.writeRoots : aBuilder.readRoots);
        serviceDirs          = new PathRoots(aBuilder.serviceDirs);
        serviceControlBinary = aBuilder.serviceControlBinary;
        maxFetchBytes        = aBuilder.maxFetchBytes;
        maxUnpackBytes       = aBuilder.maxUnpackBytes;
        maxUnpackEntries     = aBuilder.maxUnpackEntries;
        maxStepSeconds       = aBuilder.maxStepSeconds;
        maxSleepSeconds      = aBuilder.maxSleepSeconds;
        maxPlanSeconds       = aBuilder.maxPlanSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A policy for tests: everything below one directory is writable, no network host is allowed by default. */
    public static StepPolicy permissiveForTests(Path aRoot) {
        return builder()
                .allowShell(true)
                .writeRoots(Arrays.asList(aRoot.toAbsolutePath().toString()))
                .serviceDirs(Arrays.asList(aRoot.toAbsolutePath().resolve("service").toString()))
                .serviceControlBinary(aRoot.toAbsolutePath().resolve("bin/service-control"))
                .build();
    }

    public boolean isShellAllowed() {
        return allowShell;
    }

    public String getShellAllowedPrefix() {
        return shellAllowedPrefix;
    }

    public HostAllowList getFetchHosts() {
        return fetchHosts;
    }

    public HostAllowList getStatusHosts() {
        return statusHosts;
    }

    public PathRoots getWriteRoots() {
        return writeRoots;
    }

    public PathRoots getReadRoots() {
        return readRoots;
    }

    public PathRoots getServiceDirs() {
        return serviceDirs;
    }

    public Path getServiceControlBinary() {
        return serviceControlBinary;
    }

    public long getMaxFetchBytes() {
        return maxFetchBytes;
    }

    public long getMaxUnpackBytes() {
        return maxUnpackBytes;
    }

    public int getMaxUnpackEntries() {
        return maxUnpackEntries;
    }

    public int getMaxStepSeconds() {
        return maxStepSeconds;
    }

    public int getMaxSleepSeconds() {
        return maxSleepSeconds;
    }

    public int getMaxPlanSeconds() {
        return maxPlanSeconds;
    }

    @Override
    public String toString() {
        return "StepPolicy{allowShell=" + allowShell
                + ", shellAllowedPrefix='" + shellAllowedPrefix + '\''
                + ", fetchHosts=" + fetchHosts
                + ", statusHosts=" + statusHosts
                + ", writeRoots=" + writeRoots
                + ", readRoots=" + readRoots
                + ", serviceDirs=" + serviceDirs
                + ", serviceControlBinary=" + serviceControlBinary
                + ", maxFetchBytes=" + maxFetchBytes
                + ", maxUnpackBytes=" + maxUnpackBytes
                + ", maxUnpackEntries=" + maxUnpackEntries
                + ", maxStepSeconds=" + maxStepSeconds
                + ", maxSleepSeconds=" + maxSleepSeconds
                + ", maxPlanSeconds=" + maxPlanSeconds
                + '}';
    }

    public static class Builder {

        private boolean      allowShell           = true;
        private String       shellAllowedPrefix   = "";
        private List<String> fetchHosts           = new ArrayList<>();
        private List<String> statusHosts          = new ArrayList<>();
        private List<String> writeRoots           = new ArrayList<>();
        private List<String> readRoots            = new ArrayList<>();
        private List<String> serviceDirs          = new ArrayList<>();
        private Path         serviceControlBinary = Paths.get("/usr/bin/svc");
        private long         maxFetchBytes        = 1024L * 1024L * 1024L;
        private long         maxUnpackBytes       = 4L * 1024L * 1024L * 1024L;
        private int          maxUnpackEntries     = 100_000;
        private int          maxStepSeconds       = 600;
        private int          maxSleepSeconds      = 120;
        private int          maxPlanSeconds       = 570;

        public Builder allowShell(boolean aValue) {
            allowShell = aValue;
            return this;
        }

        public Builder shellAllowedPrefix(String aValue) {
            shellAllowedPrefix = aValue == null ? "" : aValue.trim();
            return this;
        }

        public Builder fetchHosts(List<String> aValue) {
            fetchHosts = new ArrayList<>(aValue);
            return this;
        }

        public Builder statusHosts(List<String> aValue) {
            statusHosts = new ArrayList<>(aValue);
            return this;
        }

        public Builder writeRoots(List<String> aValue) {
            writeRoots = new ArrayList<>(aValue);
            return this;
        }

        public Builder readRoots(List<String> aValue) {
            readRoots = new ArrayList<>(aValue);
            return this;
        }

        public Builder serviceDirs(List<String> aValue) {
            serviceDirs = new ArrayList<>(aValue);
            return this;
        }

        public Builder serviceControlBinary(Path aValue) {
            serviceControlBinary = aValue;
            return this;
        }

        public Builder maxFetchBytes(long aValue) {
            maxFetchBytes = aValue;
            return this;
        }

        public Builder maxUnpackBytes(long aValue) {
            maxUnpackBytes = aValue;
            return this;
        }

        public Builder maxUnpackEntries(int aValue) {
            maxUnpackEntries = aValue;
            return this;
        }

        public Builder maxStepSeconds(int aValue) {
            maxStepSeconds = aValue;
            return this;
        }

        public Builder maxSleepSeconds(int aValue) {
            maxSleepSeconds = aValue;
            return this;
        }

        public Builder maxPlanSeconds(int aValue) {
            maxPlanSeconds = aValue;
            return this;
        }

        public StepPolicy build() {
            return new StepPolicy(this);
        }
    }
}

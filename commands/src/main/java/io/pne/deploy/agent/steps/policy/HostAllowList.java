package io.pne.deploy.agent.steps.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Hosts a plan may talk to.
 *
 * <p>An entry is either a host name, a {@code host:port} pair, or an IPv4 range in CIDR form. A range is compared
 * against the literal address in the URL and never against a resolved name, so a name that happens to resolve into
 * an allowed range is still refused.
 */
public class HostAllowList {

    private final List<String> entries = new ArrayList<>();
    private final List<int[]>  cidrs   = new ArrayList<>();

    public HostAllowList(List<String> aEntries) {
        for (String entry : aEntries) {
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                continue;
            }
            entries.add(trimmed);
            if (trimmed.indexOf('/') > 0) {
                cidrs.add(parseCidr(trimmed));
            }
        }
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<String> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean allows(String aHost, int aPort) {
        if (aHost == null) {
            return false;
        }
        String host = aHost.toLowerCase(Locale.ROOT);
        if (entries.contains(host)) {
            return true;
        }
        if (aPort > 0 && entries.contains(host + ":" + aPort)) {
            return true;
        }
        Integer address = parseIpv4(host);
        if (address != null) {
            for (int[] cidr : cidrs) {
                if ((address & cidr[1]) == (cidr[0] & cidr[1])) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return entries.toString();
    }

    private static int[] parseCidr(String aEntry) {
        int slash = aEntry.indexOf('/');
        Integer address = parseIpv4(aEntry.substring(0, slash));
        if (address == null) {
            throw new IllegalArgumentException("Not an IPv4 range: " + aEntry);
        }
        int bits;
        try {
            bits = Integer.parseInt(aEntry.substring(slash + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not an IPv4 range: " + aEntry);
        }
        if (bits < 0 || bits > 32) {
            throw new IllegalArgumentException("Prefix length must be 0..32: " + aEntry);
        }
        int mask = bits == 0 ? 0 : (int) (0xFFFFFFFFL << (32 - bits));
        return new int[]{address, mask};
    }

    private static Integer parseIpv4(String aHost) {
        String[] parts = aHost.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int address = 0;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return null;
            }
            if (octet < 0 || octet > 255) {
                return null;
            }
            address = (address << 8) | octet;
        }
        return address;
    }
}

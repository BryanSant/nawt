package io.github.swat.spi;

import io.github.swat.Capability;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable set of {@link Capability} flags a backend declares.
 * Returned from {@link PeerFactory#capabilities()}; queryable via
 * {@code io.github.swat.Toolkit#supports(Capability)}.
 */
public final class Capabilities {
    private final EnumSet<Capability> caps;

    private Capabilities(EnumSet<Capability> caps) { this.caps = caps; }

    /** Build a {@code Capabilities} declaring the listed flags. {@code null}
     *  entries are silently dropped. */
    public static Capabilities of(Capability... caps) {
        EnumSet<Capability> set = EnumSet.noneOf(Capability.class);
        if (caps != null) {
            for (Capability c : caps) if (c != null) set.add(c);
        }
        return new Capabilities(set);
    }

    /** Empty set — backends with no opt-in capabilities. */
    public static Capabilities none() { return new Capabilities(EnumSet.noneOf(Capability.class)); }

    public boolean has(Capability c) { return c != null && caps.contains(c); }

    /** Read-only view of the declared flags. */
    public Set<Capability> all() { return Collections.unmodifiableSet(caps); }
}

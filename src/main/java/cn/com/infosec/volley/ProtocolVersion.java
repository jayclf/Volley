package cn.com.infosec.volley;

import java.io.Serializable;

/**
 * Represents a protocol version. The "major.minor" numbering
 * scheme is used to indicate versions of the protocol.
 * <p>
 * This class defines a protocol version as a combination of
 * protocol name, major version number, and minor version number.
 * Note that {@link #equals} and {@link #hashCode} are defined as
 * final here, they cannot be overridden in derived classes.
 * </p>
 *
 * @since 4.0
 */
public class ProtocolVersion implements Serializable, Cloneable {

    private static final long serialVersionUID = 8950662842175091068L;


    /** Name of the protocol. */
    protected final String protocol;

    /** Major version number of the protocol */
    protected final int major;

    /** Minor version number of the protocol */
    protected final int minor;


    /**
     * Create a protocol version designator.
     *
     * @param protocol   the name of the protocol, for example "HTTP"
     * @param major      the major version number of the protocol
     * @param minor      the minor version number of the protocol
     */
    public ProtocolVersion(String protocol, int major, int minor) {
        this.protocol = protocol;
        this.major = major;
        this.minor = minor;
    }

    /**
     * Returns the name of the protocol.
     *
     * @return the protocol name
     */
    public final String getProtocol() {
        return protocol;
    }

    /**
     * Returns the major version number of the protocol.
     *
     * @return the major version number.
     */
    public final int getMajor() {
        return major;
    }

    /**
     * Returns the minor version number of the HTTP protocol.
     *
     * @return the minor version number.
     */
    public final int getMinor() {
        return minor;
    }


    /**
     * Obtains a specific version of this protocol.
     * This can be used by derived classes to instantiate themselves instead
     * of the base class, and to define constants for commonly used versions.
     * <p>
     * The default implementation in this class returns {@code this}
     * if the version matches, and creates a new {@link ProtocolVersion}
     * otherwise.
     * </p>
     *
     * @param major     the major version
     * @param minor     the minor version
     *
     * @return  a protocol version with the same protocol name
     *          and the argument version
     */
    public ProtocolVersion forVersion(final int major, final int minor) {

        if ((major == major) && (minor == minor)) {
            return this;
        }

        // argument checking is done in the constructor
        return new ProtocolVersion(protocol, major, minor);
    }


    /**
     * Obtains a hash code consistent with {@link #equals}.
     *
     * @return  the hashcode of this protocol version
     */
    @Override
    public final int hashCode() {
        return protocol.hashCode() ^ (major * 100000) ^ minor;
    }


    /**
     * Checks equality of this protocol version with an object.
     * The object is equal if it is a protocl version with the same
     * protocol name, major version number, and minor version number.
     * The specific class of the object is <i>not</i> relevant,
     * instances of derived classes with identical attributes are
     * equal to instances of the base class and vice versa.
     *
     * @param obj       the object to compare with
     *
     * @return  {@code true} if the argument is the same protocol version,
     *          {@code false} otherwise
     */
    @Override
    public final boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProtocolVersion)) {
            return false;
        }
        final ProtocolVersion that = (ProtocolVersion) obj;

        return ((protocol.equals(that.protocol)) &&
                (major == that.major) &&
                (minor == that.minor));
    }


    /**
     * Checks whether this protocol can be compared to another one.
     * Only protocol versions with the same protocol name can be
     * {@link #compareToVersion compared}.
     *
     * @param that      the protocol version to consider
     *
     * @return  {@code true} if {@link #compareToVersion compareToVersion}
     *          can be called with the argument, {@code false} otherwise
     */
    public boolean isComparable(final ProtocolVersion that) {
        return (that != null) && protocol.equals(that.protocol);
    }


    /**
     * Compares this protocol version with another one.
     * Only protocol versions with the same protocol name can be compared.
     * This method does <i>not</i> define a total ordering, as it would be
     * required for {@link Comparable}.
     *
     * @param that      the protocol version to compare with
     *
     * @return   a negative integer, zero, or a positive integer
     *           as this version is less than, equal to, or greater than
     *           the argument version.
     *
     * @throws IllegalArgumentException
     *         if the argument has a different protocol name than this object,
     *         or if the argument is {@code null}
     */
    public int compareToVersion(final ProtocolVersion that) {
        if (null == that || !protocol.equals(that.protocol)) {
            throw new RuntimeException(String.format("Versions for different protocols cannot be compared: %s %s", this, that));
        }
        int delta = getMajor() - that.getMajor();
        if (delta == 0) {
            delta = getMinor() - that.getMinor();
        }
        return delta;
    }


    /**
     * Tests if this protocol version is greater or equal to the given one.
     *
     * @param version   the version against which to check this version
     *
     * @return  {@code true} if this protocol version is
     *          {@link #isComparable comparable} to the argument
     *          and {@link #compareToVersion compares} as greater or equal,
     *          {@code false} otherwise
     */
    public final boolean greaterEquals(final ProtocolVersion version) {
        return isComparable(version) && (compareToVersion(version) >= 0);
    }


    /**
     * Tests if this protocol version is less or equal to the given one.
     *
     * @param version   the version against which to check this version
     *
     * @return  {@code true} if this protocol version is
     *          {@link #isComparable comparable} to the argument
     *          and {@link #compareToVersion compares} as less or equal,
     *          {@code false} otherwise
     */
    public final boolean lessEquals(final ProtocolVersion version) {
        return isComparable(version) && (compareToVersion(version) <= 0);
    }


    /**
     * Converts this protocol version to a string.
     *
     * @return  a protocol version string, like "HTTP/1.1"
     */
    @Override
    public String toString() {
        return protocol +
                '/' +
                Integer.toString(major) +
                '.' +
                Integer.toString(minor);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}
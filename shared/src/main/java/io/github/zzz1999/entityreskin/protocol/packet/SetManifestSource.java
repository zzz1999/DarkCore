package io.github.zzz1999.entityreskin.protocol.packet;

import io.github.zzz1999.entityreskin.protocol.codec.PacketBuffer;

import java.util.Arrays;

/**
 * S-&gt;C: tells the client where to fetch the resource manifest, and pins the manifest's
 * expected SHA-256. The client verifies the downloaded manifest against this hash before
 * trusting any entry (hash pinning against a tampered/MITM manifest).
 */
public final class SetManifestSource implements Packet {

    private static final int MAX_URL = 2048;
    private static final int MAX_PATH = 1024;
    private static final int MAX_HASH = 64;

    private final String baseUrl;
    private final String manifestPath;
    private final byte[] manifestSha256;

    public SetManifestSource(String baseUrl, String manifestPath, byte[] manifestSha256) {
        this.baseUrl = baseUrl;
        this.manifestPath = manifestPath;
        this.manifestSha256 = manifestSha256;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String manifestPath() {
        return manifestPath;
    }

    public byte[] manifestSha256() {
        return manifestSha256;
    }

    @Override
    public int id() {
        return PacketIds.SET_MANIFEST_SOURCE;
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(baseUrl);
        buf.writeString(manifestPath);
        buf.writeBytes(manifestSha256);
    }

    public static SetManifestSource read(PacketBuffer buf) {
        String baseUrl = buf.readString(MAX_URL);
        String manifestPath = buf.readString(MAX_PATH);
        byte[] manifestSha256 = buf.readBytes(MAX_HASH);
        return new SetManifestSource(baseUrl, manifestPath, manifestSha256);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SetManifestSource)) {
            return false;
        }
        SetManifestSource other = (SetManifestSource) o;
        return baseUrl.equals(other.baseUrl)
                && manifestPath.equals(other.manifestPath)
                && Arrays.equals(manifestSha256, other.manifestSha256);
    }

    @Override
    public int hashCode() {
        int result = baseUrl.hashCode();
        result = 31 * result + manifestPath.hashCode();
        result = 31 * result + Arrays.hashCode(manifestSha256);
        return result;
    }

    @Override
    public String toString() {
        return "SetManifestSource{baseUrl='" + baseUrl + "', manifestPath='" + manifestPath
                + "', manifestSha256=" + Arrays.toString(manifestSha256) + '}';
    }
}

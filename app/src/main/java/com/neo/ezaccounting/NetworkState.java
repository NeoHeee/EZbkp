package com.neo.ezaccounting;

import android.net.Network;
import android.net.NetworkCapabilities;

import java.util.Objects;

public final class NetworkState {
    public enum Type { NONE, WIFI, CELLULAR, VPN, OTHER }

    public final Type type;
    public final Network network;
    public final boolean validated;
    public final boolean metered;

    NetworkState(Type type, Network network, boolean validated, boolean metered) {
        this.type = type == null ? Type.NONE : type;
        this.network = network;
        this.validated = validated;
        this.metered = metered;
    }

    static NetworkState from(Network network, NetworkCapabilities capabilities) {
        if (network == null || capabilities == null ||
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return none();
        }
        Type type;
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) type = Type.VPN;
        else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) type = Type.WIFI;
        else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            type = Type.CELLULAR;
        } else type = Type.OTHER;
        return new NetworkState(type, network,
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
    }

    public static NetworkState none() {
        return new NetworkState(Type.NONE, null, false, false);
    }

    public long networkHandle() {
        return network == null ? -1L : network.getNetworkHandle();
    }

    public String key() {
        return type.name() + "|" + networkHandle() + "|" + validated;
    }

    public String label() {
        switch (type) {
            case WIFI: return "Wi-Fi";
            case CELLULAR: return "移动网络";
            case VPN: return "VPN";
            case OTHER: return "其他网络";
            default: return "无网络";
        }
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof NetworkState)) return false;
        NetworkState other = (NetworkState) value;
        return type == other.type && networkHandle() == other.networkHandle() &&
                validated == other.validated && metered == other.metered;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, networkHandle(), validated, metered);
    }
}

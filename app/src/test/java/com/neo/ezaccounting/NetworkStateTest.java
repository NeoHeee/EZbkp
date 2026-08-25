package com.neo.ezaccounting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NetworkStateTest {
    @Test
    public void labelsConnectionKindsForDiagnostics() {
        assertEquals("无网络", NetworkState.none().label());
        assertEquals("Wi-Fi", new NetworkState(NetworkState.Type.WIFI,
                null, true, false).label());
        assertEquals("移动网络", new NetworkState(NetworkState.Type.CELLULAR,
                null, true, true).label());
        assertEquals("VPN", new NetworkState(NetworkState.Type.VPN,
                null, true, false).label());
    }
}

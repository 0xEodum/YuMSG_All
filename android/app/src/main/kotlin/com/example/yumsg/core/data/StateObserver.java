package com.yumsg.core.data;

import com.yumsg.core.enums.AppState;
import com.yumsg.core.enums.ConnectionState;

public interface StateObserver {
    void onStateChanged(AppState newState);
    void onConnectionStateChanged(ConnectionState newState);
}

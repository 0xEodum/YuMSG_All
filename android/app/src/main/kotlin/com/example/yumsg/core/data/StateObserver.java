package com.example.yumsg.core.data;

import com.example.yumsg.core.enums.AppState;
import com.example.yumsg.core.enums.ConnectionState;

public interface StateObserver {
    void onStateChanged(AppState newState);
    void onConnectionStateChanged(ConnectionState newState);
}

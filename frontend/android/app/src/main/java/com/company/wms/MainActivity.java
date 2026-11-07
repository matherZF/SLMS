package com.company.wms;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 全面屏适配（状态栏/手势条留白）由 Capacitor 8 SystemBars 插件 +
        // 网页侧 var(--safe-area-inset-*) / env() 完成，不在原生层重复处理，
        // 避免双重内边距。
    }
}

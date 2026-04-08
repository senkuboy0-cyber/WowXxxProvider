package com.wowxxx

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class WowXxxPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(WowXxxProvider())
    }
}

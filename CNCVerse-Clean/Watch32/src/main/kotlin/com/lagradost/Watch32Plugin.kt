package com.lagradost

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class Watch32Plugin : BasePlugin() {
    override fun load() {
        registerMainAPI(Watch32())
    }
}

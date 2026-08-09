package com.lagradost

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class HDrezkaProviderPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(HDrezkaProvider())
    }
}

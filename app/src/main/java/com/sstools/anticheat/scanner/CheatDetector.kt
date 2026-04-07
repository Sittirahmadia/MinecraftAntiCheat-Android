package com.sstools.anticheat.scanner

/**
 * Comprehensive Minecraft Cheat Detection Engine for Android
 * Ported from Python SS Tools Native - covers 1.21-1.21.11
 * Strict signature matching to avoid false flags
 */

data class CheatSignature(
    val name: String,
    val category: String,
    val severity: String, // "critical", "high", "medium", "low"
    val description: String,
    val classPatterns: List<String> = emptyList(),
    val stringPatterns: List<String> = emptyList(),
    val filePatterns: List<String> = emptyList(),
    val packagePatterns: List<String> = emptyList(),
    val minMatches: Int = 2
)

data class DetectionResult(
    val signatureName: String,
    val category: String,
    val severity: String,
    val description: String,
    val matchedPatterns: List<String>,
    val matchCount: Int,
    val filePath: String,
    val confidence: Float
)

object CheatDetector {

    // ── WHITELIST ──
    val WHITELISTED_MODS = setOf(
        "sodium", "lithium", "phosphor", "starlight", "ferritecore",
        "lazydfu", "smoothboot", "entityculling", "memoryleakfix",
        "modernfix", "immediatelyfast", "nvidium", "exordium",
        "optifine", "iris", "oculus", "canvas",
        "fabric-api", "fabricapi", "fabric_api", "forgeconfigapiport",
        "architectury", "cloth-config", "clothconfig", "modmenu",
        "midnightlib", "iceberg", "puzzleslib", "balm", "bookshelf",
        "geckolib", "playeranimator", "azurelib", "creativecore",
        "kotlinforforge", "kotlin-stdlib", "fabric-language-kotlin",
        "journeymap", "xaero", "xaerominimap", "xaerosworldmap",
        "voxelmap", "betterpingdisplay", "appleskin", "jade",
        "wthit", "hwyla", "rei", "roughlyenoughitems", "jei",
        "emi", "tooltipfix", "betterf3", "minimap",
        "capes", "customskinloader", "skinlayers3d", "ears",
        "cosmetica", "fabulousclouds", "effectivemc", "visuality",
        "lambdynamiclights", "dynamiclights", "continuity",
        "soundphysics", "presencefootsteps", "ambientsounds",
        "inventorysorter", "mousewheelie", "itemscroller",
        "litematica", "malilib", "minihud", "tweakeroo",
        "itemswapper", "zoomify", "okzoomer", "freecam",
        "replaymod", "replay", "authme", "debugify",
        "notenoughcrashes", "bettercrashes", "yosbr",
        "sodium-extra", "indium", "reeses-sodium-options",
        "borderlessmining", "dynamicfps", "enhancedblockentities",
        "chatheads", "nochatreports", "controlify",
        "worldedit", "axiom", "voicechat", "plasmovoice",
        "simplevoicechat", "emotecraft", "spark", "bobby",
        "distanthorizons", "distant-horizons", "c2me", "noisium",
    )

    // ── MOD AUTHENTICITY FINGERPRINTS ──
    val MOD_FINGERPRINTS = mapOf(
        "sodium" to listOf("me/jellysquid/mods/sodium", "net/caffeinemc/mods/sodium", "cafe/sodium"),
        "lithium" to listOf("me/jellysquid/mods/lithium", "net/caffeinemc/mods/lithium"),
        "iris" to listOf("net/coderbot/iris", "net/irisshaders"),
        "optifine" to listOf("net/optifine", "optifine/"),
        "fabric-api" to listOf("net/fabricmc/fabric"),
        "modmenu" to listOf("com/terraformersmc/modmenu"),
        "journeymap" to listOf("journeymap/"),
        "xaero" to listOf("xaero/"),
        "rei" to listOf("me/shedaniel/rei"),
        "jei" to listOf("mezz/jei"),
        "emi" to listOf("dev/emi"),
        "jade" to listOf("snownee/jade"),
        "litematica" to listOf("fi/dy/masa/litematica"),
        "malilib" to listOf("fi/dy/masa/malilib"),
        "replaymod" to listOf("com/replaymod"),
        "worldedit" to listOf("com/sk89q/worldedit"),
        "spark" to listOf("me/lucko/spark"),
        "distanthorizons" to listOf("com/seibel/distanthorizons"),
        "distant-horizons" to listOf("com/seibel/distanthorizons"),
        "architectury" to listOf("dev/architectury"),
        "geckolib" to listOf("software/bernie/geckolib"),
        "debugify" to listOf("dev/isxander/debugify"),
        "nochatreports" to listOf("com/aizistral/nochatreports"),
        "c2me" to listOf("com/ishland/c2me"),
        "modernfix" to listOf("org/embeddedt/modernfix"),
        "immediatelyfast" to listOf("net/raphimc/immediatelyfast"),
    )

    // ── CHEAT SIGNATURES ──
    val SIGNATURES: List<CheatSignature> = listOf(
        // Crystal PvP
        CheatSignature("AutoCrystal Module", "Crystal PvP", "critical",
            "Automated end crystal placement and detonation",
            classPatterns = listOf("AutoCrystal.class", "CrystalAura.class", "AutoCrystalRewrite.class", "CrystalPlacer.class", "CrystalBreaker.class"),
            stringPatterns = listOf("autocrystal", "crystalaura", "crystal_aura", "auto_crystal", "crystalPlacer", "crystalBreaker", "crystalSpeed", "placeDelay", "breakDelay", "crystalRange", "placeCrystal", "breakCrystal"),
            packagePatterns = listOf("module/combat/AutoCrystal", "module/combat/CrystalAura"),
            minMatches = 2),
        CheatSignature("AnchorMacro / AutoAnchor", "Crystal PvP", "critical",
            "Automated respawn anchor exploit for PvP",
            classPatterns = listOf("AnchorMacro.class", "AutoAnchor.class", "AnchorAura.class"),
            stringPatterns = listOf("anchormacro", "autoanchor", "anchor_macro", "auto_anchor", "anchoraura", "anchorCharge", "anchorExplode", "placeAnchor"),
            minMatches = 2),
        CheatSignature("BedAura / AutoBed", "Crystal PvP", "critical",
            "Automated bed bombing for PvP",
            classPatterns = listOf("BedAura.class", "AutoBed.class", "BedBomb.class"),
            stringPatterns = listOf("bedaura", "autobed", "bed_aura", "auto_bed", "bedBomb", "bedExplode", "placeBed"),
            minMatches = 2),
        CheatSignature("Surround / AutoTrap", "Crystal PvP", "high",
            "Automatic obsidian surrounding / trapping",
            classPatterns = listOf("Surround.class", "AutoTrap.class", "SelfTrap.class", "HoleFiller.class"),
            stringPatterns = listOf("surround", "autotrap", "auto_trap", "selftrap", "holefiller", "surroundObsidian"),
            minMatches = 2),

        // Sword PvP
        CheatSignature("AimAssist / AimBot", "Sword PvP", "critical",
            "Automatic aim correction towards players",
            classPatterns = listOf("AimAssist.class", "AimBot.class", "AutoAim.class", "SilentAim.class"),
            stringPatterns = listOf("aimassist", "aimbot", "aim_assist", "aim_bot", "autoaim", "aimSpeed", "aimRange", "aimFov", "aimSmooth", "silentAim", "aimLock"),
            packagePatterns = listOf("module/combat/AimAssist", "module/combat/AimBot"),
            minMatches = 2),
        CheatSignature("KillAura / ForceField", "Sword PvP", "critical",
            "Automatic attack on nearby entities",
            classPatterns = listOf("KillAura.class", "ForceField.class", "Aura.class", "MultiAura.class"),
            stringPatterns = listOf("killaura", "forcefield", "kill_aura", "force_field", "auraRange", "auraDelay", "auraSpeed", "attackDelay", "swingRange", "autoAttack"),
            packagePatterns = listOf("module/combat/KillAura", "module/combat/ForceField"),
            minMatches = 2),
        CheatSignature("Triggerbot", "Sword PvP", "critical",
            "Automatic attack when crosshair is on target",
            classPatterns = listOf("Triggerbot.class", "TriggerBot.class", "AutoClick.class"),
            stringPatterns = listOf("triggerbot", "trigger_bot", "triggerbotDelay", "triggerbotRange", "triggerHit", "triggerCps"),
            minMatches = 2),
        CheatSignature("Reach Hack", "Sword PvP", "critical",
            "Extended attack/interaction reach distance",
            classPatterns = listOf("Reach.class", "ReachHack.class", "HitboxExpand.class"),
            stringPatterns = listOf("reachHack", "reachDistance", "extraReach", "reachModifier", "hitboxExpand", "combatReach"),
            minMatches = 2),
        CheatSignature("Velocity / AntiKnockback", "Sword PvP", "critical",
            "Reduces or eliminates knockback",
            classPatterns = listOf("Velocity.class", "AntiKnockback.class", "AntiKB.class", "NoKnockback.class"),
            stringPatterns = listOf("velocity", "antiknockback", "anti_knockback", "antikb", "noknockback", "velocityHorizontal", "velocityVertical"),
            minMatches = 2),
        CheatSignature("AutoTotem", "Sword PvP", "high",
            "Automatically places totem in offhand",
            classPatterns = listOf("AutoTotem.class", "OffhandTotem.class"),
            stringPatterns = listOf("autototem", "auto_totem", "totemSwitch", "offhandTotem", "totemDelay"),
            minMatches = 2),
        CheatSignature("AutoClicker (Cheat)", "Sword PvP", "high",
            "Automated clicking beyond normal CPS",
            classPatterns = listOf("AutoClicker.class", "FastClick.class"),
            stringPatterns = listOf("autoclicker", "auto_clicker", "clickSpeed", "maxCps", "minCps", "leftAutoClick", "rightAutoClick", "jitterClick"),
            minMatches = 2),

        // Movement
        CheatSignature("Speed Hack", "Movement", "critical",
            "Increases movement speed beyond normal",
            classPatterns = listOf("Speed.class", "SpeedHack.class", "BHop.class"),
            stringPatterns = listOf("speedHack", "speedMode", "speedValue", "speedBoost", "bhop", "bunnyHop", "timerSpeed", "speedBypass"),
            minMatches = 3),
        CheatSignature("Fly Hack", "Movement", "critical",
            "Allows flying in survival mode",
            classPatterns = listOf("Fly.class", "FlyHack.class", "Flight.class"),
            stringPatterns = listOf("flyHack", "flySpeed", "flyMode", "flightSpeed", "flyGlide", "flyBypass", "flyAntiKick"),
            minMatches = 3),
        CheatSignature("NoFall", "Movement", "high",
            "Prevents fall damage",
            classPatterns = listOf("NoFall.class", "AntiFall.class"),
            stringPatterns = listOf("nofall", "no_fall", "antiFall", "noFallDamage", "noFallPacket"),
            minMatches = 2),
        CheatSignature("ElytraFly / ElytraBoost", "Movement", "high",
            "Enhanced elytra flight",
            classPatterns = listOf("ElytraFly.class", "ElytraBoost.class"),
            stringPatterns = listOf("elytraFly", "elytraBoost", "elytraSpeed", "elytraGlide", "elytraBypass"),
            minMatches = 2),

        // Visual
        CheatSignature("ESP / Tracers", "Visual", "high",
            "Renders player/entity outlines or tracer lines",
            classPatterns = listOf("ESP.class", "Tracers.class", "PlayerESP.class", "StorageESP.class"),
            stringPatterns = listOf("espMode", "tracerLine", "espBox", "espColor", "playerEsp", "entityEsp", "storageEsp", "chestEsp"),
            minMatches = 2),
        CheatSignature("Xray", "Visual", "critical",
            "See through blocks to find ores/caves",
            classPatterns = listOf("Xray.class", "XRay.class", "OreESP.class"),
            stringPatterns = listOf("xrayMode", "xrayBlocks", "oreHighlight", "xrayOpacity", "xrayBrightness"),
            minMatches = 2),

        // Cheat Clients
        CheatSignature("Meteor Client", "Cheat Client", "critical",
            "Meteor Client - popular Fabric cheat client",
            classPatterns = listOf("MeteorClient.class", "MeteorAddon.class"),
            stringPatterns = listOf("meteorclient", "meteor-client", "meteor.client", "meteordevelopment", "minegame159"),
            packagePatterns = listOf("meteordevelopment/meteorclient", "meteorclient/systems", "meteorclient/modules"),
            minMatches = 2),
        CheatSignature("Wurst Client", "Cheat Client", "critical",
            "Wurst Client - well-known cheat client",
            classPatterns = listOf("WurstClient.class", "WurstInitializer.class"),
            stringPatterns = listOf("wurstclient", "wurst-client", "wurst.client", "Alexander01998"),
            packagePatterns = listOf("net/wurstclient", "wurstclient/hacks"),
            minMatches = 2),
        CheatSignature("Future Client", "Cheat Client", "critical",
            "Future Client - premium cheat client",
            stringPatterns = listOf("futureclient", "future-client", "future.client"),
            packagePatterns = listOf("com/futureclient"),
            minMatches = 2),
        CheatSignature("Impact Client", "Cheat Client", "critical",
            "Impact Client - Minecraft cheat client",
            stringPatterns = listOf("impactclient", "impact-client", "impactdevelopment"),
            packagePatterns = listOf("impactclient/module", "impactdevelopment/client"),
            minMatches = 2),
        CheatSignature("LiquidBounce", "Cheat Client", "critical",
            "LiquidBounce - open source cheat client",
            stringPatterns = listOf("liquidbounce", "liquid-bounce", "LiquidBounce"),
            packagePatterns = listOf("net/ccbluex/liquidbounce"),
            minMatches = 2),
        CheatSignature("Aristois Client", "Cheat Client", "critical",
            "Aristois - modular cheat client",
            stringPatterns = listOf("aristois", "aristois.com"),
            packagePatterns = listOf("me/aristois"),
            minMatches = 2),
        CheatSignature("Phobos Client", "Cheat Client", "critical",
            "Phobos - crystal PvP cheat client",
            stringPatterns = listOf("phobosclient", "phobos-client"),
            packagePatterns = listOf("me/earth2me/phobos"),
            minMatches = 2),
        CheatSignature("Konas Client", "Cheat Client", "critical",
            "Konas - premium crystal PvP client",
            stringPatterns = listOf("konasclient", "konas-client", "konas.client"),
            packagePatterns = listOf("me/konas"),
            minMatches = 2),
        CheatSignature("RusherHack", "Cheat Client", "critical",
            "RusherHack - premium anarchy client",
            stringPatterns = listOf("rusherhack", "rusher-hack", "rusherhackclient"),
            packagePatterns = listOf("org/rusherhack"),
            minMatches = 2),
        CheatSignature("ThunderHack", "Cheat Client", "critical",
            "ThunderHack - Fabric cheat client",
            stringPatterns = listOf("thunderhack", "thunder-hack", "thunderhackrework"),
            packagePatterns = listOf("thunderhack/module"),
            minMatches = 2),
        CheatSignature("BleachHack", "Cheat Client", "critical",
            "BleachHack - Fabric cheat client",
            stringPatterns = listOf("bleachhack", "bleach-hack", "BleachHack"),
            packagePatterns = listOf("org/bleachhack"),
            minMatches = 2),
        CheatSignature("CoffeeClient", "Cheat Client", "critical",
            "Coffee Client - Fabric cheat client",
            stringPatterns = listOf("coffeeclient", "coffee-client"),
            packagePatterns = listOf("coffee/client"),
            minMatches = 2),
        CheatSignature("Salhack", "Cheat Client", "critical",
            "Salhack - cheat client",
            stringPatterns = listOf("salhack", "sal-hack"),
            packagePatterns = listOf("me/ionar2/salhack"),
            minMatches = 2),
        CheatSignature("ForgeHax", "Cheat Client", "critical",
            "ForgeHax - Forge-based cheat client",
            stringPatterns = listOf("forgehax", "forge-hax"),
            packagePatterns = listOf("com/matt/forgehax"),
            minMatches = 2),
        CheatSignature("3arthh4ck", "Cheat Client", "critical",
            "3arthh4ck - anarchy PvP client",
            stringPatterns = listOf("3arthh4ck", "earthhack", "earth-hack"),
            packagePatterns = listOf("me/earth2me/earthhack"),
            minMatches = 2),
        CheatSignature("GameSense", "Cheat Client", "critical",
            "GameSense - anarchy / crystal PvP client",
            stringPatterns = listOf("gamesenseclient", "gamesense-client"),
            packagePatterns = listOf("com/gamesense"),
            minMatches = 2),
        CheatSignature("Sigma Client", "Cheat Client", "critical",
            "Sigma - premium cheat client",
            stringPatterns = listOf("sigmaclient", "sigma-client", "sigma5"),
            packagePatterns = listOf("info/sigmaclient"),
            minMatches = 2),
        CheatSignature("Inertia Client", "Cheat Client", "critical",
            "Inertia - Fabric cheat client",
            stringPatterns = listOf("inertiaclient", "inertia-client"),
            packagePatterns = listOf("inertiaclient/module"),
            minMatches = 2),

        // Macro Tools
        CheatSignature("198Macro", "Macro Tool", "critical",
            "198Macro - crystal PvP macro tool",
            stringPatterns = listOf("198macro", "198_macro", "198 macro", "198macro.exe"),
            filePatterns = listOf("198macro", "198Macro"),
            minMatches = 1),
        CheatSignature("ZenithMacro", "Macro Tool", "critical",
            "Zenith Macro - PvP macro tool",
            stringPatterns = listOf("zenithmacro", "zenith_macro", "zenith macro"),
            filePatterns = listOf("zenithmacro", "ZenithMacro"),
            minMatches = 1),

        // Framework
        CheatSignature("ClickGUI Module System", "Cheat Framework", "critical",
            "Cheat client module/GUI system detected",
            classPatterns = listOf("ClickGUI.class", "ClickGuiModule.class", "HudEditor.class", "ModuleManager.class", "HackManager.class"),
            stringPatterns = listOf("clickGUI", "clickGui", "click_gui", "moduleManager", "hackManager", "cheatManager", "moduleCategory", "enabledModules", "toggleModule"),
            minMatches = 3),

        // Player hacks
        CheatSignature("Scaffold / AutoBridge", "Player", "critical",
            "Automatically places blocks while walking",
            classPatterns = listOf("Scaffold.class", "AutoBridge.class", "BlockFly.class"),
            stringPatterns = listOf("scaffold", "autobridge", "scaffoldWalk", "blockFly", "scaffoldDelay", "scaffoldRotation", "towerMode"),
            minMatches = 2),
        CheatSignature("Nuker / AutoMine", "Player", "critical",
            "Automatically breaks blocks in range",
            classPatterns = listOf("Nuker.class", "AutoMine.class", "PacketMine.class", "InstantMine.class"),
            stringPatterns = listOf("nukerMode", "autoMine", "packetMine", "instantMine", "speedMine", "nukerRange"),
            minMatches = 2),
        CheatSignature("Timer Hack", "Player", "critical",
            "Modifies game tick speed",
            classPatterns = listOf("Timer.class", "TimerHack.class"),
            stringPatterns = listOf("timerSpeed", "timerValue", "timerMultiplier", "gameSpeed", "tickSpeed", "timerBypass"),
            minMatches = 2),

        // Network
        CheatSignature("PacketFly", "Network", "critical",
            "Flying using packet manipulation",
            classPatterns = listOf("PacketFly.class"),
            stringPatterns = listOf("packetFly", "packetflight", "packetFlySpeed", "packetFlyMode"),
            minMatches = 2),
        CheatSignature("Disabler / AC Bypass", "Network", "critical",
            "Attempts to disable server-side anticheat",
            classPatterns = listOf("Disabler.class", "AntiCheatBypass.class"),
            stringPatterns = listOf("disabler", "anticheatBypass", "acBypass", "watchdogBypass", "vulcanBypass"),
            minMatches = 2),

        // Disguised detection
        CheatSignature("Disguised Cheat (Fake Mod)", "Evasion", "critical",
            "Cheat JAR disguised with whitelisted mod filename",
            minMatches = 1),
    )

    fun isWhitelisted(filename: String): Boolean {
        val nameLower = filename.lowercase().replace(" ", "").replace("-", "").replace("_", "")
        return WHITELISTED_MODS.any { wl ->
            val clean = wl.replace("-", "").replace("_", "")
            clean in nameLower
        }
    }

    fun verifyModAuthenticity(filename: String, classFiles: List<String>): AuthenticityResult {
        val nameLower = filename.lowercase().replace(" ", "").replace("-", "").replace("_", "")
        var matchedMod: String? = null
        for (wl in WHITELISTED_MODS) {
            val clean = wl.replace("-", "").replace("_", "")
            if (clean in nameLower) { matchedMod = wl; break }
        }
        if (matchedMod == null) return AuthenticityResult(null, true, emptyList(), emptyList(), 1f)

        val fingerprints = MOD_FINGERPRINTS[matchedMod]
            ?: return AuthenticityResult(matchedMod, true, emptyList(), emptyList(), 0.5f)

        val classPathsJoined = classFiles.joinToString(" ").lowercase()
        val found = fingerprints.filter { fp -> fp.lowercase().trimEnd('/') in classPathsJoined }

        return when {
            found.isEmpty() && classFiles.isNotEmpty() -> AuthenticityResult(matchedMod, false, fingerprints, found, 0f)
            found.isEmpty() && classFiles.isEmpty() -> AuthenticityResult(matchedMod, false, fingerprints, found, 0.2f)
            else -> AuthenticityResult(matchedMod, true, fingerprints, found, minOf(1f, found.size.toFloat() / fingerprints.size))
        }
    }

    fun detectCheats(content: String, filename: String = "", filePath: String = ""): List<DetectionResult> {
        if (filename.isNotEmpty() && isWhitelisted(filename)) return emptyList()

        val results = mutableListOf<DetectionResult>()
        val contentLower = content.lowercase()
        val filenameLower = filename.lowercase()

        for (sig in SIGNATURES) {
            if (sig.name == "Disguised Cheat (Fake Mod)") continue // Handled separately
            val matched = mutableListOf<String>()

            // String patterns
            for (pattern in sig.stringPatterns) {
                if (pattern.lowercase() in contentLower) matched.add("string:$pattern")
            }
            // Class patterns
            for (pattern in sig.classPatterns) {
                if (pattern.lowercase() in contentLower) matched.add("class:$pattern")
            }
            // Package patterns
            for (pattern in sig.packagePatterns) {
                if (pattern.lowercase().replace("/", ".") in contentLower.replace("/", "."))
                    matched.add("package:$pattern")
            }
            // File patterns
            for (pattern in sig.filePatterns) {
                if (pattern.lowercase() in filenameLower) matched.add("filename:$pattern")
            }
            // Filename string check
            for (pattern in sig.stringPatterns) {
                if (pattern.lowercase() in filenameLower) matched.add("name_match:$pattern")
            }

            if (matched.size >= sig.minMatches) {
                val confidence = minOf(1f, matched.size.toFloat() / (sig.minMatches * 3))
                results.add(DetectionResult(
                    signatureName = sig.name,
                    category = sig.category,
                    severity = sig.severity,
                    description = sig.description,
                    matchedPatterns = matched.distinct(),
                    matchCount = matched.size,
                    filePath = filePath,
                    confidence = confidence
                ))
            }
        }
        return results
    }

    fun getAllSignatures(): List<Map<String, Any>> = SIGNATURES.map {
        mapOf("name" to it.name, "category" to it.category, "severity" to it.severity,
              "description" to it.description,
              "pattern_count" to (it.stringPatterns.size + it.classPatterns.size + it.packagePatterns.size))
    }
}

data class AuthenticityResult(
    val claimedMod: String?,
    val isAuthentic: Boolean,
    val expectedPackages: List<String>,
    val foundMatching: List<String>,
    val confidence: Float
)

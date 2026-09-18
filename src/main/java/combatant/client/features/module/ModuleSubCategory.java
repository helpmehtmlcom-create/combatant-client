/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

import java.util.List;
import java.util.Locale;

/**
 * Sub-categories (tabs) for organizing modules within their main {@link ModuleCategory}.
 * Inspired by CatLean and BlackOut-CE architectures, optimized for Combatant Client.
 */
public enum ModuleSubCategory {
    ALL("All"),

    // Shared & Combat tabs
    OFFENSE("Offence"),
    DEFENSE("Defence"),
    LEGIT("Legit"),
    UTILITY("Utility"),

    // Movement tabs
    LOCOMOTION("Locomotion"),
    FLIGHT("Flight"),
    EXPLOITS("Exploits"),

    // Player tabs
    AUTOMATION("Automation"),
    INTERACTION("Interact"),

    // Visuals tabs
    ESP("ESP"),
    WORLD("World"),
    EFFECTS("Effects"),

    // Misc tabs
    INFO("Info"),

    // DonutSMP tab (shared across Player, Visuals, Misc)
    DONUTSMP("DonutSMP"),

    // Fallback for backwards compatibility
    NORMAL("Normal");

    private final String title;

    ModuleSubCategory(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    /**
     * Returns the available sub-categories (tabs) for a given parent {@link ModuleCategory}.
     */
    public static List<ModuleSubCategory> getSubCategoriesFor(ModuleCategory category) {
        if (category == null) return List.of(ALL);
        return switch (category) {
            case COMBAT -> List.of(OFFENSE, DEFENSE, LEGIT, UTILITY);
            case MOVEMENT -> List.of(LOCOMOTION, FLIGHT, LEGIT, EXPLOITS);
            case PLAYER -> List.of(AUTOMATION, INTERACTION, DONUTSMP, EXPLOITS);
            case VISUALS -> List.of(ESP, WORLD, EFFECTS, DONUTSMP);
            case MISC -> List.of(UTILITY, INFO, DONUTSMP);
        };
    }

    /**
     * Resolves the effective sub-category of a module.
     * If explicitly declared, that is returned (unless ALL or legacy NORMAL);
     * otherwise, infers the sub-category intelligently based on module name, category, and purpose.
     */
    public static ModuleSubCategory resolve(Module module) {
        if (module == null) return UTILITY;
        ModuleSubCategory declared = module.getDeclaredSubCategory();
        if (declared != null && declared != ALL && declared != NORMAL) {
            return declared;
        }

        ModuleCategory cat = module.getCategory();
        if (cat == null) return UTILITY;

        String id = module.name() != null ? module.name().toLowerCase(Locale.ROOT) : "";

        return switch (cat) {
            case COMBAT -> resolveCombat(id);
            case MOVEMENT -> resolveMovement(id);
            case PLAYER -> resolvePlayer(id);
            case VISUALS -> resolveVisuals(id);
            case MISC -> resolveMisc(id);
        };
    }

    private static ModuleSubCategory resolveCombat(String id) {
        // Legit combat
        if (id.contains("aimassist") || id.contains("jumpreset") || id.contains("hitbox")
                || id.contains("pvpcooldown") || id.contains("trigger") || id.contains("clicker")) {
            return LEGIT;
        }

        // Combat Utility
        if (id.contains("tpssync") || id.contains("backtrack") || id.contains("projectilepunch")
                || id.contains("ktleave") || id.contains("elytratarget") || id.contains("attributeswap")) {
            return UTILITY;
        }

        // Defensive combat
        if (id.contains("surround") || id.contains("blocker") || id.contains("holefill")
                || id.contains("antibed") || id.contains("anticev") || id.contains("selftrap")
                || id.contains("burrow") || id.contains("antitrap") || id.contains("autoshield")
                || id.contains("doublehand") || id.contains("rubberhand") || id.contains("autoweb")
                || id.contains("shield") || id.contains("trap") || id.contains("antitotem")) {
            return DEFENSE;
        }

        // Offensive combat (default): AutoCrystal, KillAura, AutoAttack, Criticals, Reach, MaceKill, SpearAssist,
        // BowBomb, BowSpam, AutoBow, AutoMine, AutoCity, AutoAnvil, AutoBed, AutoAnchor, SpearSwap, MaceBomber, MaceSwap, ShieldBreaker, etc.
        return OFFENSE;
    }

    private static ModuleSubCategory resolveMovement(String id) {
        // Legit movement
        if (id.contains("safewalk") || id.contains("parkour") || id.contains("noslow")
                || id.contains("velocity") || id.contains("nopush") || id.contains("jesus")
                || id.contains("nofall") || id.contains("inventorymove") || id.contains("fastswim")) {
            return LEGIT;
        }

        // Flight & vertical/aerial movement
        if (id.contains("elytrafly") || id.contains("elytrahelper") || id.contains("anchorfly")
                || id.contains("packetfly") || id.contains("autododge") || id.contains("superfirework")
                || id.contains("autotrident") || id.contains("trident") || id.contains("windjump")
                || id.contains("airjump") || id.contains("boatfly") || id.equals("flight")
                || id.endsWith("fly")) {
            return FLIGHT;
        }

        // Movement Exploits / Utility
        if (id.contains("phase") || id.contains("antivoid") || id.contains("holesnap")
                || id.contains("timer") || id.contains("freeze") || id.contains("entitycontrol")
                || id.contains("nostun") || id.contains("autohighway") || id.contains("autotunnel")
                || id.contains("portalchat")) {
            return EXPLOITS;
        }

        // Locomotion (default): Speed, Strafe, TargetStrafe, Sprint, Step, ReverseStep, FastFall, AutoWalk
        return LOCOMOTION;
    }

    private static ModuleSubCategory resolvePlayer(String id) {
        // DonutSMP
        if (id.contains("ahsniper") || id.contains("autobasedig") || id.contains("boneorder")
                || id.contains("autoconfirm") || id.contains("deliver") || id.contains("autosell")
                || id.contains("autotpa") || id.contains("fakepay") || id.contains("gamblerigger")
                || id.contains("meteorantiban") || id.contains("panicsell") || id.contains("playerdetect")
                || id.contains("rtp") || id.contains("silenthome") || id.contains("spawnerdrop")
                || id.contains("spawnerprotect") || id.contains("shitdropper")) {
            return DONUTSMP;
        }

        // Exploits
        if (id.contains("blink") || id.contains("fakelag") || id.contains("chorusexploit")
                || id.contains("portalgodmode") || id.contains("autolog") || id.contains("antihunger")) {
            return EXPLOITS;
        }

        // Automation
        if (id.contains("autoeat") || id.contains("autotool") || id.contains("autoarmor")
                || id.contains("automend") || id.contains("autoreplenish") || id.contains("autokit")
                || id.contains("inventorysorter") || id.contains("cheststealer") || id.contains("echestfarmer")
                || id.contains("storagestealer")) {
            return AUTOMATION;
        }

        // Interaction (default): Scaffold, AirPlace, FastPlace, SpeedMine, ClickPearl, Offhand, MultiTask,
        // LiquidInteract, NoDelay, NoInteract, XCarry
        return INTERACTION;
    }

    private static ModuleSubCategory resolveVisuals(String id) {
        // DonutSMP Visuals
        if (id.contains("bedrockhole") || id.contains("beehive") || id.contains("chunkfinder")
                || id.contains("chunkradar") || id.contains("directionfinder") || id.contains("extraesp")
                || id.contains("lightfinder") || id.contains("netherite") || id.contains("regionmap")
                || id.contains("shulkerviewer") || id.contains("spawnerbeacon") || id.contains("spawnernametag")
                || id.contains("stashfinder") || id.contains("armortrimhider")) {
            return DONUTSMP;
        }

        // Effects
        if (id.contains("motionblur") || id.contains("postfx") || id.contains("damagetint")
                || id.contains("hiteffect") || id.contains("killeffect") || id.contains("jumpcircles")
                || id.contains("trails") || id.contains("totemfx") || id.contains("customglint")) {
            return EFFECTS;
        }

        // World
        if (id.contains("fullbright") || id.contains("freecam") || id.contains("zoom")
                || id.contains("fovcontrol") || id.contains("cameraclip") || id.contains("aspectratio")
                || id.contains("viewmodel") || id.contains("blockhighlight") || id.contains("newchunks")
                || id.contains("worldparticles") || id.contains("worldtweaks") || id.contains("reimaginedvisual")
                || id.contains("seeinvisibles") || id.contains("crosshair") || id.contains("choruspredict")
                || id.contains("predictions") || id.contains("norender") || id.contains("tazikhat")) {
            return WORLD;
        }

        // ESP (default): ESP, BlockESP, DropESP, PortalESP, StorageESP, HoleESP, ClusterESP, BedwarsESP, DonutESP,
        // Tracers, NameTags, Chams, LogoutSpots, TargetESP, SoundESP
        return ESP;
    }

    private static ModuleSubCategory resolveMisc(String id) {
        // DonutSMP Misc
        if (id.contains("coordsnapper") || id.contains("fakeroles") || id.contains("fakestats")
                || id.contains("reloadchunk") || id.contains("staffalert") || id.contains("weathernotifier")
                || id.contains("staffdetector") || id.contains("stafftracker")) {
            return DONUTSMP;
        }

        // Info
        if (id.contains("popcounter") || id.contains("hitsounds") || id.contains("visualrange")
                || id.contains("autoez")) {
            return INFO;
        }

        // Utility (default): BetterMinecraft, ClickGui, Panic, MessageFilter, NoSound, DefineTarget,
        // FakePlayer, AutoReconnect, AutoRespawn, NameProtect
        return UTILITY;
    }
}

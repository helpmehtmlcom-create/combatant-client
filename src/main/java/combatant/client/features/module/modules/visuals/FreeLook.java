/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.entity.FreecamEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "freelook",
        displayName = "FreeLook",
        aliases = {"Perspective", "360View"},
        category = ModuleCategory.VISUALS,
        description = "Allows 360-degree free camera rotation independent of player movement."
)
public final class FreeLook extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue restorePerspective =
            bool("freeLookRestorePerspective", "restore_perspective", true);

    private CameraType prevPerspective = null;
    private FreecamEntity camEntity = null;

    private float camYaw = 0.0f;
    private float camPitch = 0.0f;

    @Override
    public void onEnable() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }

        prevPerspective = mc.options.getCameraType();
        if (prevPerspective == CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        camYaw = player.getYRot();
        camPitch = player.getXRot();

        camEntity = new FreecamEntity(mc.level);
        Vec3 pos = player.position().add(0, player.getEyeHeight(), 0);
        syncCamera(pos);
        mc.setCameraEntity(camEntity);
    }

    @Override
    public void onDisable() {
        if (restorePerspective.get() && prevPerspective != null) {
            mc.options.setCameraType(prevPerspective);
        }
        if (mc.player != null && mc.getCameraEntity() == camEntity) {
            mc.setCameraEntity(mc.player);
        }
        camEntity = null;
        prevPerspective = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || camEntity == null) {
            setEnabled(false);
            return;
        }

        Vec3 pos = player.position().add(0, player.getEyeHeight(), 0);
        syncCamera(pos);
        if (mc.getCameraEntity() != camEntity) {
            mc.setCameraEntity(camEntity);
        }
    }

    private void syncCamera(Vec3 pos) {
        if (camEntity == null) return;
        camEntity.xo = pos.x;
        camEntity.yo = pos.y;
        camEntity.zo = pos.z;
        camEntity.xOld = pos.x;
        camEntity.yOld = pos.y;
        camEntity.zOld = pos.z;
        camEntity.absSnapTo(pos.x, pos.y, pos.z);
        camEntity.setYRot(camYaw);
        camEntity.setXRot(camPitch);
        camEntity.setDeltaMovement(Vec3.ZERO);
    }
}

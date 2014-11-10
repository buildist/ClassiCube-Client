package com.mojang.minecraft.mob;

import com.mojang.minecraft.Minecraft;
import org.lwjgl.opengl.GL11;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.tile.Block;
import com.mojang.minecraft.level.tile.FireBlock;
import com.mojang.minecraft.level.tile.FlowerBlock;
import com.mojang.minecraft.model.AnimalModel;
import com.mojang.minecraft.model.HumanoidModel;
import com.mojang.minecraft.model.Model;
import com.mojang.minecraft.net.SkinDownloadThread;
import com.mojang.minecraft.render.ShapeRenderer;
import com.mojang.minecraft.render.TextureManager;
import com.mojang.minecraft.render.texture.Textures;
import com.mojang.util.LogUtil;
import java.awt.image.BufferedImage;

public class HumanoidMob extends Mob {

    protected HumanoidMob(Level level, String modelName, float posX, float posY, float posZ) {
        super(level, modelName);
        this.setPos(posX, posY, posZ);
    }

    @Override
    public void renderModel(TextureManager textures, float var2, float var3, float var4,
            float yawDegrees, float pitchDegrees, float scale) {

        // Render block model
        if (isInteger(modelName)) {
            renderBlock(textures);
            return;
        }

        // Render the rest of the model
        Model model = modelCache.getModel(modelName);
        model.render(var2, var4, tickCount + var3, yawDegrees, pitchDegrees, scale);

        // If model is humanoid, render its outer layer ("hair")
        if (hasHair && model instanceof HumanoidModel) {
            GL11.glDisable(GL11.GL_CULL_FACE);
            HumanoidModel modelHeadwear = (HumanoidModel) model;
            modelHeadwear.headwear.yaw = modelHeadwear.head.yaw;
            modelHeadwear.headwear.pitch = modelHeadwear.head.pitch;
            modelHeadwear.headwear.render(scale);
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }

    private void renderBlock(TextureManager textures) {
        try {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glPushMatrix();

            // These are here to revert the scalef calls in Mob.java.
            // While those calls are useful for entity models, they cause the
            // block models to be rendered upside down.
            GL11.glScalef(-1F, 1F, 1F);
            GL11.glScalef(1F, -1F, 1F);
            Block block = Block.blocks[Integer.parseInt(modelName)];
            // TODO: Implement proper detection of which blocks need translation.
            float yTranslation = -1.4F;
            if (block instanceof FlowerBlock || block instanceof FireBlock) {
                yTranslation = -1.8F;
            }
            GL11.glTranslatef(-0.5F, yTranslation, -0.2F);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures.load(Textures.TERRAIN));

            block.renderPreview(ShapeRenderer.instance);
            GL11.glPopMatrix();
            GL11.glDisable(GL11.GL_BLEND);
        } catch (Exception e) {
            String msg = String.format(
                    "Could not use block model \"%s\"; using humanoid model instead.",
                    modelName);
            LogUtil.logWarning(msg, e);
            setModel(Model.HUMANOID);
        }
    }

    public void renderSheep(TextureManager textureManager, float var2, float var3, float var4,
            float yawDegrees, float pitchDegrees, float scale) {
        AnimalModel model = (AnimalModel) modelCache.getModel("sheep");
        float headY = model.head.y;
        float headZ = model.head.z;
        super.renderModel(textureManager, var2, var3, var4, yawDegrees, pitchDegrees, scale);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureManager.load(Textures.SHEEP_FUR));
        AnimalModel sheepModel = (AnimalModel) modelCache.getModel("sheep.fur");
        sheepModel.head.yaw = model.head.yaw;
        sheepModel.head.pitch = model.head.pitch;
        sheepModel.head.y = model.head.y;
        sheepModel.head.x = model.head.x;
        sheepModel.body.yaw = model.body.yaw;
        sheepModel.body.pitch = model.body.pitch;
        sheepModel.leg1.pitch = model.leg1.pitch;
        sheepModel.leg2.pitch = model.leg2.pitch;
        sheepModel.leg3.pitch = model.leg3.pitch;
        sheepModel.leg4.pitch = model.leg4.pitch;
        sheepModel.head.render(scale);
        sheepModel.body.render(scale);
        sheepModel.leg1.render(scale);
        sheepModel.leg2.render(scale);
        sheepModel.leg3.render(scale);
        sheepModel.leg4.render(scale);

        model.head.y = headY;
        model.head.z = headZ;
    }

    private String skinName;
    private BufferedImage skinBitmap;
    private volatile BufferedImage newSkinBitmap;
    private volatile int textureId = -1;

    // Gets the name of the current skin. Can be 'null' (meaning 'use default').
    public String getSkinName() {
        return skinName;
    }

    // Sets model name. newName must not be null. Removes any non-standard skin.
    // For humanoid skins, setSkin() should be called with the player's name afterwards.
    public final synchronized void setModel(String newName) {
        if (null == newName) {
            throw new IllegalArgumentException("newName cannot be null");
        }
        LogUtil.logInfo("setModel(" + newName + ")");
        resetSkin();
        modelName = newName;
    }

    // Replaces ANY skin with a default texture.
    public synchronized void resetSkin() {
        skinName = null;
        newSkinBitmap = null;
    }

    // Causes current skin to be re-downloaded (if any is set).
    public void reloadSkin() {
        setSkin(skinName);
    }

    // Immediately unloads current skin texture. Should be called from main thread only.
    public synchronized void unloadSkin(TextureManager textureManager) {
        if (skinBitmap != null) {
            // Only unload texture if not default
            textureManager.unloadTexture(textureId);
        }
    }

    // Sets current skin image IF given skinName matches current skinName.
    // Can be called from any thread -- used as a callback for SkinDownloadThread.
    // Given image will be loaded next frame, in bindTexture().
    public synchronized void setSkinImage(String skinName, BufferedImage image) {
        if (this.skinName != null && this.skinName.equals(skinName)) {
            newSkinBitmap = image;
        }
    }

    public synchronized void setSkin(String skinName) {
        LogUtil.logInfo("setSkin(" + skinName + ")");
        if (skinName == null || skinName.length() == 0) {
            // Blank values of "skinName" reset skin to default.
            this.newSkinBitmap = null;
            this.skinName = null;
            return;
        }
        if (isInteger(modelName)) {
            // Skins not supported for block models
            return;
        }
        this.skinName = skinName;

        String lowercaseUrl = skinName.toLowerCase();
        boolean isFullUrl = (lowercaseUrl.startsWith("http://") || lowercaseUrl.startsWith("https://"))
                && lowercaseUrl.endsWith(".png");
        boolean isHumanoid = Model.HUMANOID.equals(modelName);

        String downloadUrl;
        if (isFullUrl) {
            // Full URL was given, download from there.
            downloadUrl = skinName;
        } else {
            // Only the player name was given. Download from skin server.
            downloadUrl = Minecraft.skinServer + skinName + ".png";
        }

        // Non-humanoid skins are only downloaded if full URL was given.
        // (See "Interaction with ExtPlayerList" in CPE ChangeModel spec)
        if (isHumanoid || isFullUrl) {
            new SkinDownloadThread(this, downloadUrl, skinName, !isHumanoid).start();
        }
    }

    @Override
    public void bindTexture(TextureManager textureManager) {
        // If skin changed, or if no skin texture is yet loaded...
        if (skinBitmap != newSkinBitmap || textureId < 0) {
            synchronized (this) {
                if (skinBitmap != newSkinBitmap || textureId < 0) {
                    if (skinBitmap != null) {
                        // Unload the old texture
                        textureManager.unloadTexture(textureId);
                    }
                    if (newSkinBitmap == null) {
                        // Load default skin
                        if (isInteger(modelName)) {
                            textureId = textureManager.load(Textures.TERRAIN);
                        } else if (Model.HUMANOID.equals(modelName)) {
                            textureId = textureManager.load(Textures.MOB_HUMANOID);
                        } else {
                            textureId = textureManager.load(Textures.forModel(modelName));
                        }
                    } else {
                        // Load custom skin
                        hasHair = Model.HUMANOID.equals(modelName) && checkForHat(newSkinBitmap);
                        textureId = textureManager.load(newSkinBitmap);
                    }
                    skinBitmap = newSkinBitmap;
                }
            }
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }
}

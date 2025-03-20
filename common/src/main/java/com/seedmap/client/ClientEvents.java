package com.seedmap.client;

import com.seedmap.SeedMapMod;
import com.seedmap.client.gui.SeedMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SeedMapMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
@OnlyIn(Dist.CLIENT)
public class ClientEvents {
    
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (SeedMapMod.KeyBindings.OPEN_MAP.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof SeedMapScreen) {
                minecraft.setScreen(null);
            } else {
                minecraft.setScreen(new SeedMapScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        SeedMapMod.KeyBindings.register(event);
    }
} 
package com.seedmap;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

@Mod(SeedMapMod.MOD_ID)
public class SeedMapMod {
    public static final String MOD_ID = "seedmap";
    
    public SeedMapMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        
        // Register the clientSetup method for modloading
        modEventBus.addListener(this::clientSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLClientSetupEvent event) {
        // Common setup code
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        // Client setup code
    }

    public static class KeyBindings {
        public static final KeyMapping OPEN_MAP = new KeyMapping(
            "key.seedmap.open_map",
            GLFW.GLFW_KEY_M,
            "key.categories.misc"
        );

        public static void register(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MAP);
        }
    }
} 
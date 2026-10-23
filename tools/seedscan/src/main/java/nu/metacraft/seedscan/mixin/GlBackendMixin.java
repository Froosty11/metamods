package nu.metacraft.seedscan.mixin;

import com.mojang.renderpearl.backend.opengl.GlBackend;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xvfb + Mesa llvmpipe offers no sRGB-capable visuals. The game never enables GL_FRAMEBUFFER_SRGB,
 * so with -Dseedscan.noSrgb the request is dropped and the window can be created headless.
 */
@Mixin(GlBackend.class)
public class GlBackendMixin {
    @Inject(method = "createWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/sdl/SDLVideo;SDL_CreateWindow(Ljava/lang/CharSequence;IIJ)J"))
    private void seedscan$noSrgb(CallbackInfoReturnable<Long> cir) {
        if (Boolean.getBoolean("seedscan.noSrgb")) {
            SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_FRAMEBUFFER_SRGB_CAPABLE, 0);
        }
    }
}
